package at.querchecker.controller;

import at.querchecker.api.config.LlmProperties;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.search.SearchProperties;
import at.querchecker.config.ProviderState;
import at.querchecker.config.ProviderStatusService;
import at.querchecker.controller.dto.ProviderSetupInitResponse;
import at.querchecker.controller.dto.ProviderSetupKeysResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Map;

/**
 * Endpoints für den Einrichtungs-Assistenten.
 *
 * GET /api/provider-setup/init — Platzhalter-Keys + Konfig-Pfade
 * GET /api/provider-setup/keys?provider=X — bestehende Key-Werte pro Provider
 */
@Slf4j
@RestController
@RequestMapping("/api/provider-setup")
@RequiredArgsConstructor
public class ProviderSetupController {

    private final ProviderStatusService providerStatusService;
    private final ProviderProperties providerProperties;
    private final SearchProperties searchProperties;
    private final LlmProperties llmProperties;

    /**
     * Liefert Platzhalter-Keys + Backend-Pfade für beide Konfig-Dateien.
     * Nur abrufbar wenn mind. ein Provider nicht VALID ist.
     */
    @GetMapping("/init")
    public ResponseEntity<ProviderSetupInitResponse> init() {
        var status = providerStatusService.getStatus();
        if (status.searchState() == ProviderState.VALID && status.llmState() == ProviderState.VALID) {
            return ResponseEntity.ok(new ProviderSetupInitResponse(Map.of(), null, null));
        }

        Map<String, String> placeholders = Map.of(
            "brave",               ProviderStatusService.BRAVE_PLACEHOLDER,
            "groq",                ProviderStatusService.GROQ_PLACEHOLDER,
            "openrouter",          ProviderStatusService.OPENROUTER_PLACEHOLDER,
            "google-discovery",    ProviderStatusService.GOOGLE_CREDENTIALS_PLACEHOLDER
        );

        return ResponseEntity.ok(new ProviderSetupInitResponse(
            placeholders,
            "../config/secrets.yml",
            "../config/querchecker.yml"
        ));
    }

    /**
     * Gibt bestehenden Key/Pfad zurück — null wenn Platzhalter erkannt.
     * Nur abrufbar wenn Provider CONFIGURED, UNREACHABLE oder UNAVAILABLE.
     *
     * Für GOOGLE_DISCOVERY: gibt credentialsPath + credentialsFileFound zurück.
     */
    @GetMapping("/keys")
    public ResponseEntity<ProviderSetupKeysResponse> getKeys(@RequestParam String provider) {
        return switch (provider.toUpperCase()) {
            case "BRAVE" -> {
                String key = providerProperties.getProvider(Provider.BRAVE).getApiKey();
                yield ResponseEntity.ok(ProviderSetupKeysResponse.forApiKey(
                    ProviderStatusService.isUnconfigured(key, ProviderStatusService.BRAVE_PLACEHOLDER) ? null : key
                ));
            }
            case "GROQ" -> {
                String key = providerProperties.getProvider(Provider.GROQ).getApiKey();
                yield ResponseEntity.ok(ProviderSetupKeysResponse.forApiKey(
                    ProviderStatusService.isUnconfigured(key, ProviderStatusService.GROQ_PLACEHOLDER) ? null : key
                ));
            }
            case "OPENROUTER" -> {
                String key = providerProperties.getProvider(Provider.OPENROUTER).getApiKey();
                yield ResponseEntity.ok(ProviderSetupKeysResponse.forApiKey(
                    ProviderStatusService.isUnconfigured(key, ProviderStatusService.OPENROUTER_PLACEHOLDER) ? null : key
                ));
            }
            case "GOOGLE_DISCOVERY" -> {
                var cfg = providerProperties.getGoogleDiscovery();
                String path = cfg != null ? cfg.getCredentialsPath() : null;
                if (ProviderStatusService.isUnconfigured(path, ProviderStatusService.GOOGLE_CREDENTIALS_PLACEHOLDER)) {
                    yield ResponseEntity.ok(ProviderSetupKeysResponse.forCredentials(null, false));
                }
                boolean fileFound = path != null && new File(path).exists();
                yield ResponseEntity.ok(ProviderSetupKeysResponse.forCredentials(path, fileFound));
            }
            default -> ResponseEntity.badRequest().build();
        };
    }
}
