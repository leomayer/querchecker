package at.querchecker.controller.dto;

import java.util.List;

/**
 * Ein Provider im Einrichtungs-Assistenten (z.B. BRAVE, GROQ).
 *
 * @param name   Provider-Bezeichner (z.B. "BRAVE", "GOOGLE_DISCOVERY", "GROQ")
 * @param label  Anzeigename (z.B. "Brave Search", "Google Discovery")
 * @param fields Konfigurationsfelder dieses Providers
 */
public record SetupProviderDto(
    String name,
    String label,
    List<SetupFieldDto> fields
) {}
