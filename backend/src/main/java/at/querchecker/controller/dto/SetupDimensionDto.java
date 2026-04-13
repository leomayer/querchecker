package at.querchecker.controller.dto;

import java.util.List;

/**
 * Eine Konfigurations-Dimension im Einrichtungs-Assistenten.
 * Jede Dimension entspricht einem Stepper-Schritt.
 *
 * @param name           Dimension-Bezeichner ("SEARCH" oder "LLM")
 * @param label          Anzeigename (z.B. "Web Search", "Textanalyse-Engine")
 * @param activeProvider Aktuell konfigurierter Provider (z.B. "BRAVE", "GROQ")
 * @param providers      Verfügbare Provider mit ihren Feldern
 */
public record SetupDimensionDto(
    String name,
    String label,
    String activeProvider,
    List<SetupProviderDto> providers
) {}
