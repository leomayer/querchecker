package at.querchecker.deepLearning.service;

import at.querchecker.api.extraction.ExtractionProviderRouter;
import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.entity.WhCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Extraction model that delegates to the configured LLM API provider (e.g. Groq).
 * Mapped to dl_model_config.model_name = 'groq'.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroqExtractionModel implements ExtractionModel {

    static final String MODEL_NAME = "groq";

    private final ExtractionProviderRouter providerRouter;
    private final DlPromptResolver promptResolver;

    @Override
    public String getName() {
        return MODEL_NAME;
    }

    @Override
    public List<ExtractionResult> extract(ItemText input, String prompt, int maxTokens) {
        try {
            WhCategory category = Optional.ofNullable(input.getWhListing())
                .map(l -> l.getWhCategory())
                .orElse(null);

            DlCategoryPrompt dlPrompt = promptResolver.resolve(category, PromptType.PRODUCT_NAME);
            String categoryName = category != null && category.getName() != null
                ? category.getName() : "Produkt";

            String term = providerRouter.getActive()
                .extractProductName(input.getTitle(), input.getDescription(), categoryName, dlPrompt);

            if (term == null || term.isBlank()) return List.of();
            // Discard hallucinated generic responses — real product names are short
            if (term.length() > 150) {
                log.warn("Groq returned suspiciously long term ({} chars) for itemText={} — discarding",
                    term.length(), input.getId());
                return List.of();
            }

            return List.of(ExtractionResult.builder()
                .term(term.trim())
                .confidence(1.0)
                .build());

        } catch (Exception e) {
            log.warn("Groq extraction failed for itemText={}: {}", input.getId(), e.getMessage());
            return List.of();
        }
    }
}
