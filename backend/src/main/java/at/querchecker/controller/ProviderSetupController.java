package at.querchecker.controller;

import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.search.SearchProperties;
import at.querchecker.config.ProviderSetupService;
import at.querchecker.config.ProviderState;
import at.querchecker.config.ProviderStatusService;
import at.querchecker.controller.dto.ProviderSetupInitResponse;
import at.querchecker.controller.dto.ProviderSetupKeysResponse;
import at.querchecker.controller.dto.ProviderSetupSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;

/**
 * Endpoints für den Einrichtungs-Assistenten.
 *
 * GET  /api/provider-setup/init — Feldstruktur + aktuelle Werte + Kommentare + canSaveToServer
 * GET  /api/provider-setup/keys?provider=X — bestehende Key-Werte pro Provider (Legacy)
 * POST /api/provider-setup/save — Schreibt secrets.yml + querchecker.yml auf den Server
 */
@Slf4j
@RestController
@RequestMapping("/api/provider-setup")
@RequiredArgsConstructor
public class ProviderSetupController {

    private final ProviderStatusService providerStatusService;
    private final ProviderSetupService providerSetupService;
    private final ProviderProperties providerProperties;
    private final SearchProperties searchProperties;

    /**
     * Liefert die vollständige Feldstruktur für den Einrichtungs-Assistenten.
     * Felder, aktuelle Werte, Kommentare aus YAML-Dateien, canSaveToServer-Flag.
     */
    @GetMapping("/init")
    public ResponseEntity<ProviderSetupInitResponse> init() {
        return ResponseEntity.ok(providerSetupService.buildInitResponse());
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

    /**
     * Schreibt die Konfiguration direkt auf den Server.
     * Fallback: Frontend bietet Download an falls canSaveToServer = false.
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, String>> save(@RequestBody ProviderSetupSaveRequest request) {
        try {
            providerSetupService.saveToServer(request);
            return ResponseEntity.ok(Map.of("status", "saved"));
        } catch (Exception e) {
            log.error("[ProviderSetup] Fehler beim Speichern", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Speichern fehlgeschlagen: " + e.getMessage()));
        }
    }
}
