package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlExtractionTerm;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static at.querchecker.deepLearning.ExtractionStatus.*;

@Service
@RequiredArgsConstructor
public class DlPersistenceService {

    private final DlExtractionTermRepository termRepo;
    private final DlExtractionRunRepository runRepo;
    private final ApplicationEventPublisher eventPublisher;

    public void saveResults(DlExtractionRun run, List<ExtractionResult> filtered) {
        filtered.forEach(r -> termRepo.save(DlExtractionTerm.builder()
            .run(run).term(r.getTerm())
            .confidence((float) r.getConfidence()).build()));

        run.setStatus(ExtractionStatus.DONE);
        run.setExtractedAt(LocalDateTime.now());
        runRepo.save(run);

        checkAllDone(run.getItemText());
    }

    void checkAllDone(ItemText itemText) {
        boolean allDone = runRepo.findByItemText(itemText).stream()
            .allMatch(r -> r.getStatus() == DONE
                        || r.getStatus() == FAILED
                        || r.getStatus() == NO_IMPLEMENTATION);
        if (allDone) {
            eventPublisher.publishEvent(new DlExtractionCompletedEvent(itemText.getId()));
        }
    }
}
