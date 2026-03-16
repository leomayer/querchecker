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
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

    @Autowired
    List<ExtractionModel> models;

    public void scheduleExtraction(ItemText itemText) {
        String prompt = promptResolver.resolve(itemText);
        String inputHash = sha256(itemText.getTitle() + itemText.getDescription());

        log.debug("scheduleExtraction: itemText={}, active models={}, registered components={}",
            itemText.getId(), modelConfigRepo.findByActiveTrue().size(), models.size());

        List<CompletableFuture<Void>> futures = modelConfigRepo.findByActiveTrue().stream()
            .filter(mc -> {
                boolean alreadyDone = runRepo.existsByItemTextAndModelConfigAndStatus(
                    itemText, mc, ExtractionStatus.DONE);
                if (alreadyDone) {
                    log.debug("Skipping model {} — already DONE for itemText {}",
                        mc.getModelName(), itemText.getId());
                }
                return !alreadyDone;
            })
            .map(mc -> {
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

                return model.map(m ->
                    CompletableFuture.<Void>runAsync(() -> {
                        log.debug("Starting async extraction: model={}, run={}",
                            m.getName(), run.getId());
                        extractionService.runModel(run);
                        log.debug("Finished async extraction: model={}, run={}, status={}",
                            m.getName(), run.getId(), run.getStatus());
                    }))
                    .orElse(CompletableFuture.completedFuture(null));
            })
            .toList();

        log.debug("Waiting for {} futures to complete", futures.size());
        if (futures.isEmpty()) {
            log.debug("All models already done for itemText={}, broadcasting immediately", itemText.getId());
            eventPublisher.publishEvent(new DlExtractionCompletedEvent(itemText.getId()));
            return;
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                log.debug("All {} extractions finished for itemText={}",
                    futures.size(), itemText.getId());
                eventPublisher.publishEvent(
                    new DlExtractionCompletedEvent(itemText.getId()));
            });
    }

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void retryStuckRuns() {
        runRepo.findByStatusAndCreatedAtBeforeEager(ExtractionStatus.INIT, LocalDateTime.now().minusMinutes(5))
            .forEach(run -> models.stream()
                .filter(m -> m.getName().equals(run.getModelConfig().getModelName()))
                .findFirst()
                .ifPresent(m -> CompletableFuture.runAsync(
                    () -> extractionService.runModel(run))));

        runRepo.findByStatusEager(ExtractionStatus.RE_EVALUATE).forEach(run -> {
            termRepo.deleteByRun(run);
            run.setStatus(ExtractionStatus.INIT);
            runRepo.save(run);
            models.stream()
                .filter(m -> m.getName().equals(run.getModelConfig().getModelName()))
                .findFirst()
                .ifPresent(m -> CompletableFuture.runAsync(
                    () -> extractionService.runModel(run)));
        });

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
