package at.querchecker.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UsageResponse {
    /** Aktiver Web-Search-Provider: BRAVE | GOOGLE_DISCOVERY */
    private String activeSearchProvider;
    /** Aktiver LLM-Provider: GROQ | OPENROUTER */
    private String activeLlmProvider;
    private ProviderUsageDto brave;
    private ProviderUsageDto googleDiscovery;
    private ProviderUsageDto groq;
    private ProviderUsageDto openRouter;
}
