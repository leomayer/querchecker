package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlModelConfig;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import at.querchecker.deepLearning.repository.DlModelConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static at.querchecker.deepLearning.ExtractionStatus.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlOrchestrationServiceTest {

    @Mock DlModelConfigRepository modelConfigRepo;
    @Mock DlExtractionRunRepository runRepo;
    @Mock DlExtractionTermRepository termRepo;
    @Mock DlPromptResolver promptResolver;
    @Mock DlExtractionService extractionService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks DlOrchestrationService service;

    @Test
    void scheduleExtraction_setsNoImplementation_whenComponentMissing() {
        DlModelConfig mc = modelConfig("unknown-model");
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc()).thenReturn(List.of(mc));
        when(runRepo.existsByItemTextAndModelConfigAndStatus(any(), any(), any()))
            .thenReturn(false);
        when(promptResolver.resolve(any())).thenReturn("prompt");
        when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.models = List.of(); // no @Component registered

        service.scheduleExtraction(new ItemText());

        verify(runRepo).save(argThat(run ->
            run.getStatus() == NO_IMPLEMENTATION));
    }

    @Test
    void scheduleExtraction_skipsDoneRuns() {
        DlModelConfig mc = modelConfig("gelectra-large-germanquad");
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc()).thenReturn(List.of(mc));
        when(runRepo.existsByItemTextAndModelConfigAndStatus(any(), eq(mc), eq(DONE)))
            .thenReturn(true);
        when(promptResolver.resolve(any())).thenReturn("prompt");
        service.models = List.of();

        service.scheduleExtraction(new ItemText());

        verify(runRepo, never()).save(any());
    }

    @Test
    void scheduleExtraction_doesNotCreateRun_beforeComponentCheck() {
        DlModelConfig mc = modelConfig("missing-model");
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc()).thenReturn(List.of(mc));
        when(runRepo.existsByItemTextAndModelConfigAndStatus(any(), any(), any()))
            .thenReturn(false);
        when(promptResolver.resolve(any())).thenReturn("prompt");
        when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.models = List.of(); // no @Component

        service.scheduleExtraction(new ItemText());

        verify(runRepo).save(argThat(run ->
            run.getStatus() != INIT));
    }

    @Test
    void scheduleExtraction_createsRunWithInit_whenComponentExists() {
        ExtractionModel model = mock(ExtractionModel.class);
        when(model.getName()).thenReturn("gelectra-large-germanquad");
        service.models = List.of(model);

        DlModelConfig mc = modelConfig("gelectra-large-germanquad");
        when(modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc()).thenReturn(List.of(mc));
        when(runRepo.existsByItemTextAndModelConfigAndStatus(any(), any(), any()))
            .thenReturn(false);
        when(promptResolver.resolve(any())).thenReturn("prompt");
        when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.scheduleExtraction(new ItemText());

        verify(runRepo).save(argThat(run ->
            run.getStatus() == INIT));
    }

    private DlModelConfig modelConfig(String name) {
        return DlModelConfig.builder().modelName(name).active(true).executionOrder(10).build();
    }
}
