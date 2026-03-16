package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.config.DlConfig;
import at.querchecker.deepLearning.entity.DlExtractionRun;
import at.querchecker.deepLearning.entity.DlModelConfig;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlExtractionServiceTest {

    @Mock List<ExtractionModel> models;
    @Mock DlFilterService filterService;
    @Mock DlPersistenceService persistenceService;
    @Mock DlExtractionRunRepository runRepo;
    @Mock DlConfig config;
    @InjectMocks DlExtractionService service;

    @Test
    void runModel_setsStatusFailed_onException() {
        ExtractionModel model = mock(ExtractionModel.class);
        when(model.getName()).thenReturn("gelectra-large-germanquad");
        when(model.extract(any(), anyString(), anyInt()))
            .thenThrow(new RuntimeException("DJL error"));
        when(models.stream()).thenReturn(java.util.stream.Stream.of(model));

        DlModelConfig modelConfig = DlModelConfig.builder()
            .modelName("gelectra-large-germanquad")
            .maxTokens(512)
            .build();
        DlExtractionRun run = DlExtractionRun.builder()
            .modelConfig(modelConfig)
            .itemText(ItemText.builder().title("Test").description("Desc").build())
            .prompt("Was ist das Produkt?")
            .status(ExtractionStatus.INIT)
            .build();

        service.runModel(run);

        assertThat(run.getStatus()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("DJL error");
        verify(runRepo, atLeastOnce()).save(run);
    }

    @Test
    void runModel_errorMessage_truncatedTo500Chars() {
        String longMessage = "x".repeat(600);
        ExtractionModel model = mock(ExtractionModel.class);
        when(model.getName()).thenReturn("gelectra-large-germanquad");
        when(model.extract(any(), anyString(), anyInt()))
            .thenThrow(new RuntimeException(longMessage));
        when(models.stream()).thenReturn(java.util.stream.Stream.of(model));

        DlModelConfig modelConfig = DlModelConfig.builder()
            .modelName("gelectra-large-germanquad")
            .maxTokens(512)
            .build();
        DlExtractionRun run = DlExtractionRun.builder()
            .modelConfig(modelConfig)
            .itemText(ItemText.builder().title("Test").description("Desc").build())
            .prompt("Was ist das Produkt?")
            .status(ExtractionStatus.INIT)
            .build();

        service.runModel(run);

        assertThat(run.getErrorMessage()).hasSizeLessThanOrEqualTo(500);
    }
}
