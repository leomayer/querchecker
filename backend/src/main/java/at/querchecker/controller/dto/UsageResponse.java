package at.querchecker.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

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
    /** Aufschlüsselung nach Modell — aktuell nur für Groq befüllt */
    private List<ModelUsageDto> groqModelBreakdown;
}
