package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlExtractionTerm;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DlPersistenceService {

    private final DlExtractionTermRepository termRepo;
    private final DlExtractionRunRepository runRepo;
    private final ApplicationEventPublisher eventPublisher;

    public void saveResults(DlExtractionRun run, List<ExtractionResult> filtered, long durationMs) {
        filtered.forEach(r -> termRepo.save(DlExtractionTerm.builder()
            .run(run).term(r.getTerm())
            .confidence((float) r.getConfidence()).build()));

        run.setStatus(ExtractionStatus.DONE);
        run.setExtractedAt(LocalDateTime.now());
        run.setDurationMs(durationMs);
        runRepo.save(run);

        eventPublisher.publishEvent(new DlExtractionCompletedEvent(
            run.getItemText().getId(), run.getModelConfig().getModelName()));
    }
}
