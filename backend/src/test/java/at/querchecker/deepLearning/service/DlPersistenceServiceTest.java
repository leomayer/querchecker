package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlModelConfig;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlPersistenceServiceTest {

    @Mock DlExtractionTermRepository termRepo;
    @Mock DlExtractionRunRepository runRepo;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks DlPersistenceService service;

    @Test
    void saveResults_firesEventWithCorrectItemTextIdAndModelName() {
        DlExtractionRun run = runFor(42L, "test-model");

        service.saveResults(run, List.of(), 0L);

        ArgumentCaptor<DlExtractionCompletedEvent> captor =
            ArgumentCaptor.forClass(DlExtractionCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getItemTextId()).isEqualTo(42L);
        assertThat(captor.getValue().getModelName()).isEqualTo("test-model");
    }

    @Test
    void saveResults_alwaysFiresEvent_evenWithNoTerms() {
        service.saveResults(runFor(1L, "some-model"), List.of(), 0L);

        verify(eventPublisher, times(1)).publishEvent(any(DlExtractionCompletedEvent.class));
    }

    @Test
    void saveResults_alwaysFiresEvent_withTerms() {
        ExtractionResult result = ExtractionResult.builder().term("Product X").confidence(0.9).build();

        service.saveResults(runFor(7L, "some-model"), List.of(result), 123L);

        verify(eventPublisher, times(1)).publishEvent(any(DlExtractionCompletedEvent.class));
        verify(termRepo, times(1)).save(any());
    }

    private DlExtractionRun runFor(Long itemTextId, String modelName) {
        ItemText itemText = new ItemText();
        itemText.setId(itemTextId);
        DlModelConfig config = DlModelConfig.builder().modelName(modelName).build();
        return DlExtractionRun.builder()
            .itemText(itemText)
            .modelConfig(config)
            .build();
    }
}
