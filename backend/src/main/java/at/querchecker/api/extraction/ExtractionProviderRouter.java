package at.querchecker.api.extraction;

import at.querchecker.api.config.LlmProperties;
import at.querchecker.api.entity.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Leitet Extraction-Calls an den konfigurierten aktiven Provider weiter.
 * Aktiver Provider: querchecker.llm.external-provider in config/querchecker.yml
 */
@Service
@RequiredArgsConstructor
public class ExtractionProviderRouter {

    private final LlmProperties llmProperties;
    private final List<ExtractionClient> clients;

    public ExtractionClient getActive() {
        Provider active = llmProperties.getExternalProvider();
        return clients.stream()
            .filter(c -> c.getProvider() == active)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Kein ExtractionClient für Provider: " + active));
    }
}
