package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.deepLearning.repository.DlCategoryPromptRepository;
import at.querchecker.repository.WhCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void seedIfAbsent_doesNothing_whenBothTypesAlreadySeeded() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.countByPromptType(PromptType.PRODUCT_NAME)).thenReturn(3L);
        when(promptRepo.countByPromptType(PromptType.QUICK_FACTS)).thenReturn(2L);
        seeder.seedIfAbsent();
        verify(promptRepo, never()).save(any());
    }

    @Test
    void seedIfAbsent_savesDefaultProductNamePrompt_whenMissing() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.countByPromptType(PromptType.PRODUCT_NAME)).thenReturn(0L);
        when(promptRepo.countByPromptType(PromptType.QUICK_FACTS)).thenReturn(2L);
        when(categoryRepo.findByLevelOrderByNameAsc(0)).thenReturn(Collections.emptyList());

        seeder.seedIfAbsent();

        verify(promptRepo, atLeastOnce()).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.PRODUCT_NAME));
    }

    @Test
    void seedIfAbsent_savesDefaultQuickFactsPrompt_whenMissing() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.countByPromptType(PromptType.PRODUCT_NAME)).thenReturn(3L);
        when(promptRepo.countByPromptType(PromptType.QUICK_FACTS)).thenReturn(0L);

        seeder.seedIfAbsent();

        verify(promptRepo, atLeastOnce()).save(argThat(p ->
            p.getWhCategory() == null && p.getPromptType() == PromptType.QUICK_FACTS));
    }

    @Test
    void seedIfAbsent_seedsBothTypes_whenBothMissing() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.countByPromptType(PromptType.PRODUCT_NAME)).thenReturn(0L);
        when(promptRepo.countByPromptType(PromptType.QUICK_FACTS)).thenReturn(0L);
        when(categoryRepo.findByLevelOrderByNameAsc(0)).thenReturn(Collections.emptyList());

        seeder.seedIfAbsent();

        // At minimum the two default prompts (one per type)
        verify(promptRepo, atLeast(2)).save(any());
    }
}
