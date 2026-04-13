package at.querchecker.controller.dto;

import java.util.List;

/**
 * Antwort von GET /api/provider-setup/init.
 * Liefert die vollständige Feldstruktur für den Einrichtungs-Assistenten.
 *
 * @param canSaveToServer true wenn Backend Schreibzugriff auf Konfig-Dateien hat
 * @param secretsYmlPath  Pfad zur secrets.yml (relativ zum Backend-Verzeichnis)
 * @param quercheckerYmlPath Pfad zur querchecker.yml
 * @param dimensions      Konfigurations-Dimensionen (SEARCH, LLM) mit Providern + Feldern
 */
public record ProviderSetupInitResponse(
    boolean canSaveToServer,
    String secretsYmlPath,
    String quercheckerYmlPath,
    List<SetupDimensionDto> dimensions
) {}
