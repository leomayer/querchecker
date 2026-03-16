package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.DlCategoryPromptDefinitions;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.ItemText;
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
     * Resolves the extraction prompt for an ItemText.
     * Walks up the category hierarchy (deepest → root) looking for a matching prompt.
     * Falls back to the DB default prompt, then to hardcoded DEFAULT.
     *
     * If the resolved prompt contains {category}, it is replaced with the name of the
     * deepest (most specific) category of the listing — e.g. "Notebooks" instead of
     * "Computer / Software".
     */
    public String resolve(ItemText itemText) {
        WhCategory deepestCategory = Optional.ofNullable(itemText.getWhListing())
            .map(listing -> listing.getWhCategory())
            .orElse(null);

        // Walk up category tree looking for a prompt
        WhCategory category = deepestCategory;
        while (category != null) {
            Optional<DlCategoryPrompt> prompt = promptRepo.findByWhCategory(category);
            if (prompt.isPresent()) {
                return interpolate(prompt.get().getPrompt(), deepestCategory);
            }
            category = category.getParent();
        }

        // Fallback: default DB prompt or hardcoded
        String fallback = promptRepo.findDefault()
            .map(DlCategoryPrompt::getPrompt)
            .orElse(DlCategoryPromptDefinitions.DEFAULT);
        return interpolate(fallback, deepestCategory);
    }

    private String interpolate(String prompt, WhCategory deepestCategory) {
        if (!prompt.contains("{category}")) return prompt;
        String name = deepestCategory != null && deepestCategory.getName() != null
            ? deepestCategory.getName()
            : "Produkt";
        return prompt.replace("{category}", name);
    }
}
