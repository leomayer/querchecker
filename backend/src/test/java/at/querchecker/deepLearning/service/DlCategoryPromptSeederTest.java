package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.repository.DlCategoryPromptRepository;
import at.querchecker.repository.WhCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

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
    void seedIfAbsent_doesNothing_whenAlreadySeeded() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.count()).thenReturn(3L);
        seeder.seedIfAbsent();
        verify(promptRepo, never()).save(any());
    }

    @Test
    void seedIfAbsent_savesDefaultPrompt_whenCategoriesPresent() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.count()).thenReturn(0L);
        when(categoryRepo.findByLevelOrderByNameAsc(0)).thenReturn(Collections.emptyList());
        seeder.seedIfAbsent();
        verify(promptRepo, atLeastOnce()).save(argThat(p -> p.getWhCategory() == null));
    }
}
