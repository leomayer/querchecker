package at.querchecker.controller.dto;

import java.util.Map;

/**
 * Antwort von GET /api/provider-setup/init.
 * Liefert Platzhalter-Keys + Backend-Pfade für beide Konfig-Dateien.
 */
public record ProviderSetupInitResponse(
    Map<String, String> placeholderKeys,
    String secretYmlPath,
    String quercheckerYmlPath
) {}
