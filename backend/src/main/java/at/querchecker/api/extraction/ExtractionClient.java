package at.querchecker.api.extraction;

import at.querchecker.api.entity.Provider;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.research.model.BraveResult;
import at.querchecker.research.model.QuickFactsResult;

import java.util.List;

/**
 * Provider-unabhängiges Interface für LLM-Extraktion.
 * Implementierungen: GroqExtractionClient, OpenRouterExtractionClient
 * Aktiver Provider: querchecker.api.extraction.active-provider in application.yml
 */
public interface ExtractionClient {

    Provider getProvider();

    /** Produktname aus Inseratstext — kurzer String, kein JSON */
    String extractProductName(
        String title,
        String description,
        String categoryName,
        DlCategoryPrompt prompt  // aus DB, aufgelöst via DlPromptResolver
    );

    /** Quick Facts + icecatId aus Brave-Snippets — JSON-Response */
    QuickFactsResult extractQuickFacts(
        String lookupTerm,
        String categoryName,
        List<BraveResult> braveResults,
        List<String> mandatoryFields,
        DlCategoryPrompt prompt  // aus DB, aufgelöst via DlPromptResolver
    );
}
