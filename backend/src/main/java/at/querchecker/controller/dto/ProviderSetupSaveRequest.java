package at.querchecker.controller.dto;

import java.util.Map;

/**
 * Request-Body für POST /api/provider-setup/save.
 * Enthält die vom Assistenten gesammelten Werte — aufgeteilt nach Ziel-Datei.
 *
 * @param secrets     Map von Feld-Schlüsseln → Werten für secrets.yml (API-Keys, Credentials-Pfade)
 * @param config      Map von Feld-Schlüsseln → Werten für querchecker.yml (active-provider, Modellnamen, etc.)
 * @param searchProvider  Gewählter Web-Search-Provider (z.B. "BRAVE", "GOOGLE_DISCOVERY")
 * @param llmProvider     Gewählter LLM-Provider (z.B. "GROQ", "OPENROUTER", "LOCAL")
 */
public record ProviderSetupSaveRequest(
    Map<String, String> secrets,
    Map<String, String> config,
    String searchProvider,
    String llmProvider
) {}
