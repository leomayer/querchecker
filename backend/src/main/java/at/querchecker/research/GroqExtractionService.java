package at.querchecker.research;

import at.querchecker.api.extraction.ExtractionClient;
import at.querchecker.api.extraction.ExtractionProviderRouter;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.deepLearning.service.DlPromptResolver;
import at.querchecker.entity.WhCategory;
import at.querchecker.research.model.BraveResult;
import at.querchecker.research.model.QuickFactsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade für LLM-basierte Quick-Facts-Extraktion aus Brave-Suchergebnissen.
 * Delegiert an den aktiven ExtractionClient (Groq oder OpenRouter).
 */
@Service
@RequiredArgsConstructor
public class GroqExtractionService {

    private final ExtractionProviderRouter router;
    private final DlPromptResolver promptResolver;

    /**
     * Extrahiert technische Specs (Quick Facts) aus Brave-Suchergebnissen.
     *
     * @param braveResults   Suchergebnisse aus der Brave Search API
     * @param mandatoryFields Pflichtfelder aus der Kategorie-Präferenz
     * @return QuickFactsResult mit extractierten Specs und optionaler icecatId
     */
    public QuickFactsResult extractFromSnippets(String lookupTerm,
                                                WhCategory whCategory,
                                                List<BraveResult> braveResults,
                                                List<String> mandatoryFields) {
        ExtractionClient client = router.getActive();
        DlCategoryPrompt prompt = promptResolver.resolve(whCategory, PromptType.QUICK_FACTS);
        String categoryName = whCategory != null && whCategory.getName() != null
            ? whCategory.getName() : "";
        return client.extractQuickFacts(lookupTerm, categoryName, braveResults, mandatoryFields, prompt);
    }
}
