package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.config.DlConfig;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlModelConfig;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.extraction.ExtractionModel;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlExtractionServiceTest {

    @Mock ObjectProvider<List<ExtractionModel>> modelsProvider;
    @Mock DlFilterService filterService;
    @Mock DlPersistenceService persistenceService;
    @Mock DlExtractionRunRepository runRepo;
    @Mock DlConfig config;
    @InjectMocks DlExtractionService service;

    ExtractionModel model;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        model = mock(ExtractionModel.class);
        when(model.getName()).thenReturn("gelectra-large-germanquad");
        lenient().when(modelsProvider.getIfAvailable(any(Supplier.class))).thenReturn(List.of(model));
    }

    @Test
    void runModel_setsStatusFailed_onException() {
        when(model.extract(any(), anyString(), anyInt()))
            .thenThrow(new RuntimeException("DJL error"));

        service.runModel(buildRun());

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(persistenceService).saveFailed(any(), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("DJL error");
    }

    @Test
    void runModel_errorMessage_truncatedTo500Chars() {
        String longMessage = "x".repeat(600);
        when(model.extract(any(), anyString(), anyInt()))
            .thenThrow(new RuntimeException(longMessage));

        service.runModel(buildRun());

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(persistenceService).saveFailed(any(), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).hasSizeLessThanOrEqualTo(500);
    }

    private DlExtractionRun buildRun() {
        DlModelConfig modelConfig = DlModelConfig.builder()
            .modelName("gelectra-large-germanquad")
            .maxTokens(512)
            .build();
        return DlExtractionRun.builder()
            .modelConfig(modelConfig)
            .itemText(ItemText.builder().title("Test").description("Desc").build())
            .prompt("Was ist das Produkt?")
            .status(ExtractionStatus.INIT)
            .build();
    }
}
