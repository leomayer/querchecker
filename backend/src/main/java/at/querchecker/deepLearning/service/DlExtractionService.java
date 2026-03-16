package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.config.DlConfig;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlExtractionService {

    private final List<ExtractionModel> models;
    private final DlFilterService filterService;
    private final DlPersistenceService persistenceService;
    private final DlExtractionRunRepository runRepo;
    private final DlConfig config;

    public void runModel(DlExtractionRun run) {
        run.setStatus(ExtractionStatus.PENDING);
        run.setErrorMessage(null);
        runRepo.save(run);

        try {
            ExtractionModel model = models.stream()
                .filter(m -> m.getName().equals(run.getModelConfig().getModelName()))
                .findFirst().orElseThrow();

            int maxTokens = run.getModelConfig().getMaxTokens();
            List<ExtractionResult> raw = model.extract(
                run.getItemText(), run.getPrompt(), maxTokens);
            List<ExtractionResult> filtered = filterService.filter(raw, config);
            persistenceService.saveResults(run, filtered);

        } catch (Exception e) {
            run.setStatus(ExtractionStatus.FAILED);
            run.setErrorMessage(truncateError(e));
            runRepo.save(run);
            log.error("Extraction failed for run {}", run.getId(), e);
        }
    }

    private String truncateError(Exception e) {
        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
        return msg.length() > 500 ? msg.substring(0, 497) + "..." : msg;
    }
}
