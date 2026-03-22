package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.deepLearning.repository.DlCategoryPromptRepository;
import at.querchecker.entity.WhCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DlPromptResolver {

    private final DlCategoryPromptRepository promptRepo;

    /**
     * Löst Prompt auf: Eigene Kategorie → Eltern → ... → Default (whCategory=null).
     * Wirft IllegalStateException wenn kein Default-Prompt in der DB vorhanden.
     */
    public DlCategoryPrompt resolve(WhCategory category, PromptType promptType) {
        return resolveRecursive(category, promptType);
    }

    /**
     * Legacy-API für den bestehenden DL-Extraction-Pipeline.
     * Löst den PRODUCT_NAME-Prompt auf und gibt den interpolierten userPrompt zurück.
     */
    public String resolve(ItemText itemText) {
        WhCategory deepestCategory = Optional.ofNullable(itemText.getWhListing())
            .map(listing -> listing.getWhCategory())
            .orElse(null);

        DlCategoryPrompt prompt = resolveRecursive(deepestCategory, PromptType.PRODUCT_NAME);
        return interpolate(prompt.getUserPrompt(), deepestCategory);
    }

    private DlCategoryPrompt resolveRecursive(WhCategory category, PromptType promptType) {
        if (category == null) {
            return promptRepo.findDefaultByPromptType(promptType)
                .orElseThrow(() -> new IllegalStateException(
                    "Kein Default-Prompt für PromptType: " + promptType));
        }
        return promptRepo.findByWhCategoryAndPromptType(category, promptType)
            .orElseGet(() -> resolveRecursive(category.getParent(), promptType));
    }

    private String interpolate(String prompt, WhCategory deepestCategory) {
        if (!prompt.contains("{category}")) return prompt;
        String name = deepestCategory != null && deepestCategory.getName() != null
            ? deepestCategory.getName()
            : "Produkt";
        return prompt.replace("{category}", name);
    }
}
