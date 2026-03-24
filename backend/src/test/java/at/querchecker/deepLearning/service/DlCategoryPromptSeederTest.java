package at.querchecker.deepLearning.service;

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
    void seedIfAbsent_doesNothing_whenAllDefaultsAlreadySeeded() {
        when(categoryRepo.count()).thenReturn(5L);
        // All defaults present — findByName returns empty by default (no category-specific saves)
        when(promptRepo.findDefaultByPromptType(PromptType.PRODUCT_NAME))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));
        when(promptRepo.findDefaultByPromptType(PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));
        when(promptRepo.findDefaultByPromptType(PromptType.HTML_FULL_SPECS))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));
        seeder.seedIfAbsent();
        verify(promptRepo, never()).save(any());
    }

    @Test
    void seedIfAbsent_savesDefaultProductNamePrompt_whenMissing() {
        when(categoryRepo.count()).thenReturn(5L);
        // PRODUCT_NAME default missing, others already present
        when(promptRepo.findDefaultByPromptType(PromptType.PRODUCT_NAME)).thenReturn(Optional.empty());
        when(promptRepo.findDefaultByPromptType(PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));
        when(promptRepo.findDefaultByPromptType(PromptType.HTML_FULL_SPECS))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));

        seeder.seedIfAbsent();

        verify(promptRepo, times(1)).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.PRODUCT_NAME));
    }

    @Test
    void seedIfAbsent_savesDefaultQuickFactsPrompt_whenMissing() {
        when(categoryRepo.count()).thenReturn(5L);
        // QUICK_FACTS default missing, others already present
        when(promptRepo.findDefaultByPromptType(PromptType.PRODUCT_NAME))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));
        when(promptRepo.findDefaultByPromptType(PromptType.QUICK_FACTS)).thenReturn(Optional.empty());
        when(promptRepo.findDefaultByPromptType(PromptType.HTML_FULL_SPECS))
            .thenReturn(Optional.of(mock(DlCategoryPrompt.class)));

        seeder.seedIfAbsent();

        verify(promptRepo, times(1)).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.QUICK_FACTS));
    }

    @Test
    void seedIfAbsent_savesAllDefaults_whenAllMissing() {
        when(categoryRepo.count()).thenReturn(5L);
        // All defaults missing, findByName returns empty → no category-specific saves
        when(promptRepo.findDefaultByPromptType(any())).thenReturn(Optional.empty());

        seeder.seedIfAbsent();

        verify(promptRepo, times(1)).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.PRODUCT_NAME));
        verify(promptRepo, times(1)).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.QUICK_FACTS));
        verify(promptRepo, times(1)).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.HTML_FULL_SPECS));
    }
}
