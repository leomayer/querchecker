package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static at.querchecker.deepLearning.ExtractionStatus.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlPersistenceServiceTest {

    @Mock DlExtractionTermRepository termRepo;
    @Mock DlExtractionRunRepository runRepo;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks DlPersistenceService service;

    @Test
    void checkAllDone_firesEvent_whenAllRunsDone() {
        ItemText item = new ItemText();
        List<DlExtractionRun> runs = List.of(
            runWithStatus(DONE), runWithStatus(DONE));
        when(runRepo.findByItemText(item)).thenReturn(runs);

        service.checkAllDone(item);

        verify(eventPublisher).publishEvent(any(DlExtractionCompletedEvent.class));
    }

    @Test
    void checkAllDone_firesEvent_whenMixOfDoneAndFailed() {
        ItemText item = new ItemText();
        when(runRepo.findByItemText(item)).thenReturn(List.of(
            runWithStatus(DONE), runWithStatus(FAILED)));

        service.checkAllDone(item);

        verify(eventPublisher).publishEvent(any(DlExtractionCompletedEvent.class));
    }

    @Test
    void checkAllDone_firesEvent_whenNoImplementationIsTerminal() {
        ItemText item = new ItemText();
        when(runRepo.findByItemText(item)).thenReturn(List.of(
            runWithStatus(DONE), runWithStatus(NO_IMPLEMENTATION)));

        service.checkAllDone(item);

        verify(eventPublisher).publishEvent(any(DlExtractionCompletedEvent.class));
    }

    @Test
    void checkAllDone_doesNotFireEvent_whenPendingRunExists() {
        ItemText item = new ItemText();
        when(runRepo.findByItemText(item)).thenReturn(List.of(
            runWithStatus(DONE), runWithStatus(PENDING)));

        service.checkAllDone(item);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void checkAllDone_doesNotFireEvent_whenInitRunExists() {
        ItemText item = new ItemText();
        when(runRepo.findByItemText(item)).thenReturn(List.of(
            runWithStatus(DONE), runWithStatus(INIT)));

        service.checkAllDone(item);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private DlExtractionRun runWithStatus(ExtractionStatus status) {
        return DlExtractionRun.builder().status(status).build();
    }
}
