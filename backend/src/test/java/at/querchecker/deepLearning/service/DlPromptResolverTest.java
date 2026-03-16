package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlCategoryPromptDefinitions;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.repository.DlCategoryPromptRepository;
import at.querchecker.entity.WhCategory;
import at.querchecker.entity.WhListing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlPromptResolverTest {

    @Mock
    DlCategoryPromptRepository promptRepo;

    @InjectMocks
    DlPromptResolver resolver;

    @Test
    void resolve_returnsCategorySpecificPrompt() {
        WhCategory cat = new WhCategory();
        WhListing listing = WhListing.builder().whCategory(cat).build();
        ItemText item = ItemText.builder().whListing(listing).build();
        DlCategoryPrompt prompt = DlCategoryPrompt.builder()
            .prompt("Welches Laptop?").build();

        when(promptRepo.findByWhCategory(cat)).thenReturn(Optional.of(prompt));

        assertThat(resolver.resolve(item)).isEqualTo("Welches Laptop?");
    }

    @Test
    void resolve_fallsBackToDefault_whenNoCategoryMatch() {
        WhCategory cat = new WhCategory();
        WhListing listing = WhListing.builder().whCategory(cat).build();
        ItemText item = ItemText.builder().whListing(listing).build();
        DlCategoryPrompt defaultPrompt = DlCategoryPrompt.builder()
            .prompt("Default Prompt").build();

        when(promptRepo.findByWhCategory(cat)).thenReturn(Optional.empty());
        when(promptRepo.findDefault()).thenReturn(Optional.of(defaultPrompt));

        assertThat(resolver.resolve(item)).isEqualTo("Default Prompt");
    }

    @Test
    void resolve_fallsBackToHardcoded_whenDBEmpty() {
        ItemText item = ItemText.builder().whListing(null).build();

        when(promptRepo.findDefault()).thenReturn(Optional.empty());

        assertThat(resolver.resolve(item))
            .isEqualTo(DlCategoryPromptDefinitions.DEFAULT);
    }

    @Test
    void resolve_walksUpParentChain_whenDeepCategoryHasNoPrompt() {
        WhCategory root = WhCategory.builder().name("Root").build();
        WhCategory sub = WhCategory.builder().name("Sub").parent(root).build();
        WhListing listing = WhListing.builder().whCategory(sub).build();
        ItemText item = ItemText.builder().whListing(listing).build();

        when(promptRepo.findByWhCategory(sub)).thenReturn(Optional.empty());
        when(promptRepo.findByWhCategory(root)).thenReturn(Optional.of(
            DlCategoryPrompt.builder().prompt("Root prompt").build()));

        assertThat(resolver.resolve(item)).isEqualTo("Root prompt");
    }

    @Test
    void resolve_fallsBackToDefault_whenWhListingNull() {
        ItemText item = ItemText.builder().whListing(null).build();
        DlCategoryPrompt defaultPrompt = DlCategoryPrompt.builder()
            .prompt("Default Prompt").build();

        when(promptRepo.findDefault()).thenReturn(Optional.of(defaultPrompt));

        assertThat(resolver.resolve(item)).isEqualTo("Default Prompt");
    }
}
