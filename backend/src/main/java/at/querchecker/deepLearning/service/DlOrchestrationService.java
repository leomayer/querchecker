package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import at.querchecker.deepLearning.repository.DlModelConfigRepository;
import at.querchecker.entity.AppConfig;
import at.querchecker.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlOrchestrationService {

    private final DlModelConfigRepository modelConfigRepo;
    private final DlExtractionRunRepository runRepo;
    private final DlExtractionTermRepository termRepo;
    private final DlPromptResolver promptResolver;
    private final DlExtractionService extractionService;
    private final ApplicationEventPublisher eventPublisher;
    private final AppConfigRepository appConfigRepo;

    // Single-threaded: guarantees only one model runs at a time across all items
    private final ExecutorService extractionExecutor = Executors.newSingleThreadExecutor();

    @Autowired
    List<ExtractionModel> models;

    public void scheduleExtraction(ItemText itemText) {
        String prompt = promptResolver.resolve(itemText);
        String inputHash = sha256(itemText.getTitle() + itemText.getDescription());

        var activeModels = modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc();
        log.debug("scheduleExtraction: itemText={}, active models={}, registered components={}",
            itemText.getId(), activeModels.size(), models.size());

        record RunEntry(DlExtractionRun run, ExtractionModel model) {}

        List<RunEntry> entries = activeModels.stream()
            .filter(mc -> {
                boolean alreadyDone = runRepo.existsByItemTextAndModelConfigAndStatus(
                    itemText, mc, ExtractionStatus.DONE);
                if (alreadyDone) {
                    log.debug("Skipping model {} — already DONE for itemText {}, broadcasting",
                        mc.getModelName(), itemText.getId());
                    eventPublisher.publishEvent(
                        new DlExtractionCompletedEvent(itemText.getId(), mc.getModelName()));
                }
                return !alreadyDone;
            })
            .flatMap(mc -> {
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

                return model.map(m -> new RunEntry(run, m)).stream();
            })
            .toList();

        // Chain extractions sequentially in executionOrder
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (RunEntry entry : entries) {
            chain = chain.thenRunAsync(() -> {
                log.debug("Starting sequential extraction: model={}, run={}",
                    entry.model().getName(), entry.run().getId());
                extractionService.runModel(entry.run());
                log.debug("Finished sequential extraction: model={}, run={}",
                    entry.model().getName(), entry.run().getId());
            }, extractionExecutor);
        }

        log.debug("Scheduled {} sequential extractions for itemText={}", entries.size(), itemText.getId());
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
