package at.querchecker.controller;

import at.querchecker.api.config.LlmMode;
import at.querchecker.api.config.LlmProperties;
import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.search.SearchProperties;
import at.querchecker.api.search.SearchProvider;
import at.querchecker.controller.dto.ProviderStatusDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "Config", description = "Provider-Konfigurationsstatus")
public class ConfigController {

    private final ProviderProperties providerProperties;
    private final LlmProperties llmProperties;
    private final SearchProperties searchProperties;

    @GetMapping("/providers")
    @Operation(summary = "Provider-Konfigurationsstatus", description =
        "Liefert je Provider: API-Key vorhanden, Limits konfiguriert, aktuell aktiv.")
    public List<ProviderStatusDto> getProviderStatus() {
        return List.of(
            buildStatus(Provider.GROQ,             isLlmActive(Provider.GROQ)),
            buildStatus(Provider.OPENROUTER,       isLlmActive(Provider.OPENROUTER)),
            buildStatus(Provider.BRAVE,            isSearchActive(SearchProvider.BRAVE)),
            buildStatus(Provider.GOOGLE_DISCOVERY, isSearchActive(SearchProvider.GOOGLE_DISCOVERY))
        );
    }

    private ProviderStatusDto buildStatus(Provider provider, boolean active) {
        ProviderConfig cfg = providerProperties.getProvider(provider);
        boolean limitsConfigured = cfg != null;
        boolean keyPresent = limitsConfigured && cfg.getApiKey() != null && !cfg.getApiKey().isBlank();
        return new ProviderStatusDto(
            provider.name(),
            provider.getDisplayName(),
            keyPresent,
            limitsConfigured,
            active
        );
    }

    private boolean isLlmActive(Provider provider) {
        return llmProperties.getMode() == LlmMode.API
            && llmProperties.getExternalProvider() == provider;
    }

    private boolean isSearchActive(SearchProvider searchProvider) {
        return searchProperties.getActiveProvider() == searchProvider;
    }
}
