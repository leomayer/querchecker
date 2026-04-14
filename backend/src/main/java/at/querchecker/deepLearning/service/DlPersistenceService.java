package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlExtractionTerm;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlPersistenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DlExtractionTermRepository termRepo;
    private final DlExtractionRunRepository runRepo;
    private final ApplicationEventPublisher eventPublisher;

    public void saveResults(DlExtractionRun run, List<ExtractionResult> filtered, long durationMs) {
        filtered.forEach(r -> termRepo.save(DlExtractionTerm.builder()
            .run(run).term(r.getTerm())
            .confidence((float) r.getConfidence())
            .condensedSpecsJson(toJson(r.getCondensedSpec()))
            .extractedModel(r.getExtractedModel())
            .build()));

        run.setStatus(ExtractionStatus.DONE);
        run.setExtractedAt(LocalDateTime.now());
        run.setDurationMs(durationMs);
        runRepo.save(run);

        eventPublisher.publishEvent(new DlExtractionCompletedEvent(
            run.getItemText().getId(), run.getModelConfig().getModelName(), ExtractionStatus.DONE));
    }

    public void saveFailed(DlExtractionRun run, String errorMessage) {
        run.setStatus(ExtractionStatus.FAILED);
        run.setErrorMessage(errorMessage);
        runRepo.save(run);

        eventPublisher.publishEvent(new DlExtractionCompletedEvent(
            run.getItemText().getId(), run.getModelConfig().getModelName(), ExtractionStatus.FAILED));
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize condensedSpec to JSON: {}", e.getMessage());
            return null;
        }
    }
}
