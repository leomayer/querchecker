package at.querchecker.controller.dto;

/**
 * Status eines API-Providers — API-Key vorhanden, Limits konfiguriert, aktuell aktiv.
 */
public record ProviderStatusDto(
    String provider,
    String displayName,
    boolean keyPresent,
    boolean limitsConfigured,
    boolean active
) {}
