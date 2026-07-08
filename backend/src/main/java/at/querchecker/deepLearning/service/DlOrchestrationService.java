package at.querchecker.deepLearning.service;

import at.querchecker.auth.AccessKeyUsageService;
import at.querchecker.auth.QuerCheckerPrincipal;
import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import at.querchecker.deepLearning.extraction.ExtractionModel;
import at.querchecker.deepLearning.repository.DlModelConfigRepository;
import at.querchecker.entity.AppConfig;
import at.querchecker.repository.AppConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlOrchestrationService {

    // Ebene-2b (Konzept Kap. 4): DL-Extraktion läuft automatisch pro Detailansicht, nicht auf
    // explizite Anfrage — daher kein sichtbares Kontingent, sondern ein stiller Hintergrund-Schutz
    // aus Rate-Limit (Burst) + großzügigem Tagesvolumen (Gesamtkosten). Bei Blockade werden die
    // gerade erstellten Runs CANCELLED statt enqueued — der bestehende CANCELLED-Retry-Mechanismus
    // (erneutes Öffnen der Detailansicht) übernimmt das spätere Nachholen automatisch.
    private static final Duration EXTRACTION_MIN_INTERVAL = Duration.ofSeconds(2);
    private final ConcurrentHashMap<Long, Instant> lastExtractionAt = new ConcurrentHashMap<>();

    private final DlModelConfigRepository modelConfigRepo;
    private final DlExtractionRunRepository runRepo;
    private final DlExtractionTermRepository termRepo;
    private final DlPromptResolver promptResolver;
    private final DlExtractionService extractionService;
    private final ApplicationEventPublisher eventPublisher;
    private final AppConfigRepository appConfigRepo;
    private final AccessKeyUsageService accessKeyUsageService;
    private final ObjectProvider<List<ExtractionModel>> modelsProvider;

    // Lazily resolved list of models — available after bean registration
    // Package-private for testing
    List<ExtractionModel> models;

    // Unbounded deque — limit enforced manually via pollLast() in scheduleExtraction()
    private LinkedBlockingDeque<Runnable> queue;
    private ThreadPoolExecutor extractionExecutor;

    @PostConstruct
    public void init() {
        queue = new LinkedBlockingDeque<>();
        extractionExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue);
        // Pre-start the single worker thread so it blocks on queue.take().
        // Without this, addFirst() has no thread to wake up → tasks never run.
        extractionExecutor.prestartCoreThread();

        // Lazily resolve models — they are registered after context is ready
        this.models = modelsProvider != null ? modelsProvider.getIfAvailable(() -> List.of()) : List.of();
        if (models.isEmpty()) {
            log.warn("No extraction models registered at init time. Models will be registered by DlModelConfiguration after context is ready.");
        }
    }

    public synchronized void scheduleExtraction(ItemText itemText) {
        String prompt = promptResolver.resolve(itemText);
        String inputHash = sha256(itemText.getTitle() + itemText.getDescription());

        // Ensure models are resolved (in case this is called before ApplicationReadyEvent)
        if (models.isEmpty() && modelsProvider != null) {
            this.models = modelsProvider.getIfAvailable(() -> List.of());
        }

        var activeModels = modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc();
        log.debug("scheduleExtraction: itemText={}, active models={}, registered components={}",
            itemText.getId(), activeModels.size(), models.size());

        List<ExtractionTask> newTasks = new ArrayList<>();

        for (var mc : activeModels) {
            // Broadcast and skip if already DONE
            if (runRepo.existsByItemTextAndModelConfigAndStatus(itemText, mc, ExtractionStatus.DONE)) {
                log.debug("Skipping model {} — already DONE for itemText {}, broadcasting",
                    mc.getModelName(), itemText.getId());
                eventPublisher.publishEvent(
                    new DlExtractionCompletedEvent(itemText.getId(), mc.getModelName(), ExtractionStatus.DONE.name()));
                continue;
            }
            // Skip if already scheduled — avoid duplicates on rapid re-open
            if (runRepo.existsByItemTextAndModelConfigAndStatusIn(itemText, mc,
                    List.of(ExtractionStatus.INIT, ExtractionStatus.PENDING))) {
                log.debug("Skipping model {} — already INIT/PENDING for itemText {}",
                    mc.getModelName(), itemText.getId());
                continue;
            }

            Optional<ExtractionModel> model = models.stream()
                .filter(m -> m.getName().equals(mc.getModelName()))
                .findFirst();

            ExtractionStatus status = model.isPresent()
                ? ExtractionStatus.INIT
                : ExtractionStatus.NO_IMPLEMENTATION;
            log.debug("Creating run for model={}, status={}, component={}",
                mc.getModelName(), status, model.isPresent() ? "found" : "MISSING");

            DlExtractionRun run = runRepo.save(DlExtractionRun.builder()
                .itemText(itemText)
                .modelConfig(mc)
                .prompt(prompt)
                .inputHash(inputHash)
                .status(status)
                .build());

            model.ifPresent(m -> newTasks.add(new ExtractionTask(run, extractionService)));
        }

        if (!newTasks.isEmpty()) {
            String blockReason = checkExtractionBlock(itemText);
            if (blockReason != null) {
                newTasks.forEach(t -> {
                    t.getRun().setStatus(ExtractionStatus.CANCELLED);
                    runRepo.save(t.getRun());
                    // Transient reason for the UI (RATE_LIMITED / EXTRACTION_QUOTA_EXCEEDED) —
                    // persisted status stays CANCELLED, this is purely a notification.
                    eventPublisher.publishEvent(new DlExtractionCompletedEvent(
                        itemText.getId(), t.getRun().getModelConfig().getModelName(), blockReason));
                });
                return;
            }
        }

        // Insert in DESC executionOrder → addFirst() leaves lowest executionOrder at front
        newTasks.stream()
            .sorted(Comparator.comparingInt(
                (ExtractionTask t) -> t.getRun().getModelConfig().getExecutionOrder()).reversed())
            .forEach(t -> queue.addFirst(t));

        // Trim from back: oldest/lowest-priority waiting tasks become CANCELLED
        int limit = getQueueLimit();
        while (queue.size() > limit) {
            Runnable removed = queue.pollLast();
            if (removed instanceof ExtractionTask t) {
                log.debug("CANCELLED run={} (model={}) — queue overflow",
                    t.getRun().getId(), t.getRun().getModelConfig().getModelName());
                t.getRun().setStatus(ExtractionStatus.CANCELLED);
                runRepo.save(t.getRun());
            }
        }

        log.debug("Scheduled {} new tasks for itemText={}, queue size={}",
            newTasks.size(), itemText.getId(), queue.size());
    }

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void retryStuckRuns() {
        List<DlExtractionRun> stuckRuns = runRepo
            .findByStatusAndCreatedAtBeforeEager(ExtractionStatus.INIT, LocalDateTime.now().minusMinutes(5))
            .stream()
            .sorted(Comparator.comparing(r -> r.getModelConfig().getExecutionOrder()))
            .toList();

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (DlExtractionRun run : stuckRuns) {
            chain = chain.thenRunAsync(() ->
                models.stream()
                    .filter(m -> m.getName().equals(run.getModelConfig().getModelName()))
                    .findFirst()
                    .ifPresent(m -> extractionService.runModel(run)), extractionExecutor);
        }

        List<DlExtractionRun> reEvalRuns = runRepo.findByStatusEager(ExtractionStatus.RE_EVALUATE)
            .stream()
            .sorted(Comparator.comparing(r -> r.getModelConfig().getExecutionOrder()))
            .toList();

        for (DlExtractionRun run : reEvalRuns) {
            termRepo.deleteByRun(run);
            run.setStatus(ExtractionStatus.INIT);
            runRepo.save(run);
            chain = chain.thenRunAsync(() ->
                models.stream()
                    .filter(m -> m.getName().equals(run.getModelConfig().getModelName()))
                    .findFirst()
                    .ifPresent(m -> extractionService.runModel(run)), extractionExecutor);
        }

        LocalDateTime now = LocalDateTime.now();
        appConfigRepo.save(AppConfig.builder()
            .key("dl.last_retry_run")
            .value(now.toString())
            .description("Last execution of DlOrchestrationService.retryStuckRuns()")
            .updatedAt(now)
            .build());
    }

    /**
     * Rate-Limit (Burst-Schutz, 2s) + Tagesvolumen (Kosten-Schutz, 5x Lookup-Kontingent) für
     * DL-Extraktion. {@code null} (nicht blockiert) für SUPERUSER/GUEST (kein accessKeyId).
     * Verbraucht bei Freigabe sofort einen Slot, damit parallele scheduleExtraction()-Aufrufe
     * (synchronized) korrekt gegeneinander zählen.
     *
     * @return {@code null} wenn erlaubt, sonst der Blockade-Grund ("RATE_LIMITED" oder
     *         "EXTRACTION_QUOTA_EXCEEDED") — an die UI durchgereicht für eine differenzierte,
     *         aber zahlenlose Meldung (still, kein Blocking-Card wie bei Spec-Lookup-Kontingent).
     */
    private String checkExtractionBlock(ItemText itemText) {
        Long accessKeyId = QuerCheckerPrincipal.resolveCurrentAccessKeyId();
        if (accessKeyId == null) return null;

        Instant now = Instant.now();
        Instant last = lastExtractionAt.get(accessKeyId);
        if (last != null && Duration.between(last, now).compareTo(EXTRACTION_MIN_INTERVAL) < 0) {
            log.debug("Extraction rate-limited for accessKeyId={} — cancelling new runs for itemText={}",
                accessKeyId, itemText.getId());
            return "RATE_LIMITED";
        }
        if (!accessKeyUsageService.checkExtractionQuota(accessKeyId)) {
            log.debug("Extraction daily volume cap reached for accessKeyId={} — cancelling new runs for itemText={}",
                accessKeyId, itemText.getId());
            return "EXTRACTION_QUOTA_EXCEEDED";
        }

        lastExtractionAt.put(accessKeyId, now);
        accessKeyUsageService.consumeExtraction(accessKeyId);
        return null;
    }

    private int getQueueLimit() {
        return appConfigRepo.findById("dl.queue.limit")
            .map(c -> Integer.parseInt(c.getValue()))
            .orElse(10);
    }

    // Package-private for testing
    LinkedBlockingDeque<Runnable> getQueue() {
        return queue;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
