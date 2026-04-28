package at.querchecker.config;

import at.querchecker.api.config.LlmMode;
import at.querchecker.api.config.LlmProperties;
import at.querchecker.api.extraction.ExtractionProviderRouter;
import at.querchecker.api.search.WebSearchProviderRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Führt minimale Verbindungstests für aktive Provider durch.
 * Die Statusaktualisierung (markValid / markUnreachable / markUnavailable)
 * erfolgt intern in den jeweiligen Service-Implementierungen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderTestService {

    private final WebSearchProviderRouter webSearchProviderRouter;
    private final ExtractionProviderRouter extractionProviderRouter;
    private final LlmProperties llmProperties;

    public boolean isSearchDimension(String provider) {
        return "BRAVE".equals(provider) || "GOOGLE_DISCOVERY".equals(provider);
    }

    /** Führt den Test für den angegebenen Provider-Namen durch. */
    public void test(String provider) {
        if (isSearchDimension(provider)) {
            log.info("[ProviderTest] Teste Web-Search-Provider: {}", provider);
            webSearchProviderRouter.getActive().testConnection();
        } else {
            if (llmProperties.getMode() == LlmMode.LOCAL) {
                log.info("[ProviderTest] LOCAL-Modus — kein Test-Button vorgesehen");
                return;
            }
            log.info("[ProviderTest] Teste LLM-Provider: {}", provider);
            extractionProviderRouter.getActive().testConnection();
        }
    }
}
