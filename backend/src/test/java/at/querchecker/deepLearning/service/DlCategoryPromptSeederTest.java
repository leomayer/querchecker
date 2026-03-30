package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlCategoryPromptDefinitions;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.deepLearning.repository.DlCategoryPromptRepository;
import at.querchecker.repository.WhCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlCategoryPromptSeederTest {

    @Mock WhCategoryRepository categoryRepo;
    @Mock DlCategoryPromptRepository promptRepo;
    @InjectMocks DlCategoryPromptSeeder seeder;

    @Test
    void seedIfAbsent_doesNothing_whenCategoriesEmpty() {
        when(categoryRepo.count()).thenReturn(0L);
        seeder.seedIfAbsent();
        verify(promptRepo, never()).save(any());
    }

    @Test
    void seedIfAbsent_upsertsAllDefaults_evenWhenAlreadyPresent() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.findDefaultByPromptType(any()))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));

        seeder.seedIfAbsent();

        // Upsert: always saves all 3 defaults to apply current definitions
        verify(promptRepo, times(3)).save(any());
    }

    @Test
    void seedIfAbsent_insertsAllDefaults_whenAllMissing() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.findDefaultByPromptType(any())).thenReturn(Optional.empty());

        seeder.seedIfAbsent();

        verify(promptRepo, times(1)).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.PRODUCT_NAME));
        verify(promptRepo, times(1)).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.QUICK_FACTS));
        verify(promptRepo, times(1)).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.HTML_FULL_SPECS));
    }

    @Test
    void seedIfAbsent_appliesCurrentDefinition_toExistingDefault() {
        when(categoryRepo.count()).thenReturn(5L);
        DlCategoryPrompt existing = new DlCategoryPrompt();
        when(promptRepo.findDefaultByPromptType(PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(existing));
        when(promptRepo.findDefaultByPromptType(PromptType.PRODUCT_NAME))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));
        when(promptRepo.findDefaultByPromptType(PromptType.HTML_FULL_SPECS))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));

        seeder.seedIfAbsent();

        assertThat(existing.getSystemPrompt()).isEqualTo(DlCategoryPromptDefinitions.QUICK_FACTS_SYSTEM);
        assertThat(existing.getUserPrompt()).isEqualTo(DlCategoryPromptDefinitions.QUICK_FACTS_USER_DEFAULT);
    }

    @Test
    void seedIfAbsent_insertsDefault_whenMissing_andUpdatesOthers() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.findDefaultByPromptType(PromptType.PRODUCT_NAME)).thenReturn(Optional.empty());
        when(promptRepo.findDefaultByPromptType(PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));
        when(promptRepo.findDefaultByPromptType(PromptType.HTML_FULL_SPECS))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));

        seeder.seedIfAbsent();

        // All 3 are saved: 1 new insert + 2 updates
        verify(promptRepo, times(3)).save(any());
        verify(promptRepo, times(1)).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.PRODUCT_NAME));
    }
}
