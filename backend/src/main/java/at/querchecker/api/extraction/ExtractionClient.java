package at.querchecker.api.extraction;

import at.querchecker.api.entity.Provider;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.model.SearchResult;

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
        DlCategoryPrompt prompt
    );

    /** Quick Facts + sourceUrl aus Brave-Snippets — JSON-Response (Snippets-Pfad: ICECAT, GENERIC) */
    QuickFactsResult extractQuickFacts(
        String lookupTerm,
        String categoryName,
        List<SearchResult> braveResults,
        List<String> mandatoryFields,
        DlCategoryPrompt prompt
    );

    /** Quick Facts aus bereinigtem HTML-Text — JSON-Response (HTML-Fetch-Pfad: FLATPANELSHD, GSMARENA) */
    QuickFactsResult extractQuickFactsFromText(
        String lookupTerm,
        String categoryName,
        String pageText,
        List<String> mandatoryFields,
        DlCategoryPrompt prompt
    );
}
