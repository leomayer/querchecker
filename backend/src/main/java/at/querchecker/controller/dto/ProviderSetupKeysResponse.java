package at.querchecker.controller.dto;

/**
 * Antwort von GET /api/provider-setup/keys?provider=X.
 *
 * Für API-Key-basierte Provider (BRAVE, GROQ, OPENROUTER):
 *   apiKey = bestehender Key oder null wenn Platzhalter erkannt
 *   credentialsPath = null
 *   credentialsFileFound = null
 *
 * Für GOOGLE_DISCOVERY:
 *   apiKey = null
 *   credentialsPath = konfigurierter Pfad (oder null wenn Platzhalter)
 *   credentialsFileFound = true/false — ob Datei unter diesem Pfad existiert
 */
public record ProviderSetupKeysResponse(
    String apiKey,
    String credentialsPath,
    Boolean credentialsFileFound
) {
    public static ProviderSetupKeysResponse forApiKey(String key) {
        return new ProviderSetupKeysResponse(key, null, null);
    }

    public static ProviderSetupKeysResponse forCredentials(String path, boolean fileFound) {
        return new ProviderSetupKeysResponse(null, path, fileFound);
    }
}
