package at.querchecker.controller.dto;

/**
 * Ein einzelnes Konfigurationsfeld im Einrichtungs-Assistenten.
 *
 * @param key         YAML-Schlüsselname (z.B. "api-key", "model", "credentials-path")
 * @param value       Aktueller Wert — bei Secret-Feldern immer null (nicht exponiert)
 * @param placeholder Platzhalter-String (nur für Secret-Felder, sonst null)
 * @param hint        Beschreibung aus YAML-Kommentar (z.B. "Brave Search API-Key — https://...")
 * @param secret      true = gehört in secrets.yml, false = gehört in querchecker.yml
 * @param configured  true = Feld hat bereits einen gültigen Wert (bei Secrets: Key existiert, wird aber nicht gezeigt)
 */
public record SetupFieldDto(
    String key,
    String value,
    String placeholder,
    String hint,
    boolean secret,
    boolean configured
) {}
