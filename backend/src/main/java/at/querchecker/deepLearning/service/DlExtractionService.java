package at.querchecker.deepLearning.service;

import at.querchecker.api.exception.RateLimitException;
import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.config.DlConfig;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.extraction.ExtractionModel;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.sse.ErrorNotificationPayload;
import at.querchecker.sse.SseEvent;
import at.querchecker.sse.SseHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlExtractionService {

    private static final int MAX_AUTO_RETRY_SECONDS = 20;

    private final ObjectProvider<List<ExtractionModel>> modelsProvider;
    private final DlFilterService filterService;
    private final DlPersistenceService persistenceService;
    private final DlExtractionRunRepository runRepo;
    private final DlConfig config;
    private final SseHub sseHub;
    private final WhItemRepository whItemRepository;

    public void runModel(DlExtractionRun run) {
        run.setStatus(ExtractionStatus.PENDING);
        run.setErrorMessage(null);
        runRepo.save(run);

        List<ExtractionModel> models = modelsProvider.getIfAvailable(() -> List.of());
        ExtractionModel model = models.stream()
                .filter(m -> m.getName().equals(run.getModelConfig().getModelName()))
                .findFirst().orElseThrow();

        int maxTokens = run.getModelConfig().getMaxTokens();

        try {
            doExtract(run, model, maxTokens);
        } catch (RateLimitException rle) {
            log.warn("Extraction rate limited by {} — retryAfter={}s, run={}", rle.getProvider(), rle.getRetryAfterSeconds(), run.getId());
            if (rle.getRetryAfterSeconds() <= MAX_AUTO_RETRY_SECONDS) {
                sleepQuietly(rle.getRetryAfterSeconds() * 1000L + 500L);
                try {
                    doExtract(run, model, maxTokens);
                } catch (Exception retryEx) {
                    log.error("Rate-limit retry failed for run {}", run.getId(), retryEx);
                    persistenceService.saveFailed(run, truncateError(retryEx));
                    broadcastErrorNotification(run, "RATE_LIMITED", "LLM-Kontingent erreicht — bitte später erneut versuchen", (long) rle.getRetryAfterSeconds());
                }
            } else {
                persistenceService.saveFailed(run, "Rate limited: retry in " + rle.getRetryAfterSeconds() + "s");
                broadcastErrorNotification(run, "RATE_LIMITED", "LLM-Kontingent erreicht — bitte später erneut versuchen", (long) rle.getRetryAfterSeconds());
            }
        } catch (Exception e) {
            log.error("Extraction failed for run {}", run.getId(), e);
            persistenceService.saveFailed(run, truncateError(e));
            String errorType = e.getMessage() != null && e.getMessage().contains("timeout") ? "API_TIMEOUT" : "EXTRACTION_FAILED";
            String message = errorType.equals("API_TIMEOUT")
                ? "Anfrage-Timeout — Server antwortet nicht rechtzeitig"
                : "Produktextraktion fehlgeschlagen — bitte später erneut versuchen";
            broadcastErrorNotification(run, errorType, message, null);
        }
    }

    private void broadcastErrorNotification(DlExtractionRun run, String errorType, String message, Long retryAfterSeconds) {
        try {
            Long whItemId = whItemRepository.findIdByItemTextId(run.getItemText().getId())
                    .orElse(null);
            if (whItemId == null) {
                log.debug("No WhItem found for itemTextId={}, skipping error notification broadcast", run.getItemText().getId());
                return;
            }
            ErrorNotificationPayload payload = new ErrorNotificationPayload(errorType, message, retryAfterSeconds);
            SseEvent<ErrorNotificationPayload> event = new SseEvent<>("error-notification", whItemId, payload);
            sseHub.broadcast("error-notification", event);
        } catch (Exception e) {
            log.warn("Failed to broadcast error notification for run {}", run.getId(), e);
        }
    }

    private void doExtract(DlExtractionRun run, ExtractionModel model, int maxTokens) {
        long startMs = System.currentTimeMillis();
        List<ExtractionResult> raw = model.extract(run.getItemText(), run.getPrompt(), maxTokens);
        long durationMs = System.currentTimeMillis() - startMs;
        List<ExtractionResult> filtered = filterService.filter(raw, config);
        persistenceService.saveResults(run, filtered, durationMs);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncateError(Exception e) {
        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
        return msg.length() > 500 ? msg.substring(0, 497) + "..." : msg;
    }
}
