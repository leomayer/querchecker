package at.querchecker.config;

import at.querchecker.api.config.LlmMode;
import at.querchecker.api.config.LlmProperties;
import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.search.SearchProperties;
import at.querchecker.controller.dto.*;
import at.querchecker.controller.dto.ProviderSetupSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Baut die Datenstruktur für den Einrichtungs-Assistenten.
 * Felder, aktuelle Werte und Kommentare aus den YAML-Dateien.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderSetupService {

    private static final Path EXAMPLE_YML = Path.of("../config/example.yml");
    private static final Path QUERCHECKER_YML = Path.of("../config/querchecker.yml");
    private static final String SECRETS_YML_PATH = "../config/secrets.yml";
    private static final String QUERCHECKER_YML_PATH = "../config/querchecker.yml";

    private final ProviderProperties providerProperties;
    private final SearchProperties searchProperties;
    private final LlmProperties llmProperties;
    private final YamlCommentParser yamlCommentParser;
    private final ApplicationContext applicationContext;

    public ProviderSetupInitResponse buildInitResponse() {
        Map<String, String> exampleComments = yamlCommentParser.parseComments(EXAMPLE_YML);
        Map<String, String> configComments = yamlCommentParser.parseComments(QUERCHECKER_YML);

        return new ProviderSetupInitResponse(
            checkCanSaveToServer(),
            SECRETS_YML_PATH,
            QUERCHECKER_YML_PATH,
            List.of(
                buildSearchDimension(exampleComments, configComments),
                buildLlmDimension(exampleComments, configComments)
            )
        );
    }

    private boolean checkCanSaveToServer() {
        Path secretsPath = Path.of(SECRETS_YML_PATH);
        Path quercheckerPath = Path.of(QUERCHECKER_YML_PATH);
        // Prüfe ob Elternverzeichnis beschreibbar ist (für neue Dateien)
        // oder ob die Dateien selbst beschreibbar sind (für Updates)
        boolean secretsWritable = Files.exists(secretsPath)
            ? Files.isWritable(secretsPath)
            : Files.isWritable(secretsPath.getParent() != null ? secretsPath.getParent() : Path.of("."));
        boolean configWritable = Files.exists(quercheckerPath) && Files.isWritable(quercheckerPath);
        return secretsWritable && configWritable;
    }

    // ---- SEARCH dimension ----

    private SetupDimensionDto buildSearchDimension(Map<String, String> exampleComments, Map<String, String> configComments) {
        String activeProvider = searchProperties.getActiveProvider().name();
        return new SetupDimensionDto(
            "SEARCH", "Web Search", activeProvider,
            List.of(
                buildBraveProvider(exampleComments),
                buildGoogleDiscoveryProvider(exampleComments, configComments)
            )
        );
    }

    private SetupProviderDto buildBraveProvider(Map<String, String> exampleComments) {
        ProviderConfig cfg = providerProperties.getProvider(Provider.BRAVE);
        String apiKey = cfg != null ? cfg.getApiKey() : null;

        return new SetupProviderDto("BRAVE", "Brave Search", List.of(
            new SetupFieldDto(
                "api-key",
                null,
                ProviderStatusService.BRAVE_PLACEHOLDER,
                exampleComments.getOrDefault("querchecker.api.limits.brave.api-key", "Brave Search API-Key"),
                true,
                isSecretConfigured(apiKey, ProviderStatusService.BRAVE_PLACEHOLDER)
            )
        ));
    }

    private SetupProviderDto buildGoogleDiscoveryProvider(Map<String, String> exampleComments, Map<String, String> configComments) {
        var gd = providerProperties.getGoogleDiscovery();
        List<SetupFieldDto> fields = new ArrayList<>();

        String credPath = gd != null ? gd.getCredentialsPath() : null;
        // credentials-path ist kein API-Key — gehört in querchecker.yml, nicht secrets.yml
        fields.add(new SetupFieldDto(
            "credentials-path",
            credPath,
            null,
            configComments.getOrDefault("querchecker.api.google-discovery.credentials-path", "Pfad zur GCP credentials.json"),
            false,
            credPath != null && !credPath.isBlank() && !credPath.equals(ProviderStatusService.GOOGLE_CREDENTIALS_PLACEHOLDER)
        ));

        String projectId = gd != null ? gd.getProjectId() : null;
        fields.add(new SetupFieldDto(
            "project-id",
            projectId,
            null,
            configComments.getOrDefault("querchecker.api.google-discovery.project-id", "GCP Project-ID"),
            false,
            projectId != null && !projectId.isBlank()
        ));

        String engineId = gd != null ? gd.getEngineId() : null;
        fields.add(new SetupFieldDto(
            "engine-id",
            engineId,
            null,
            configComments.getOrDefault("querchecker.api.google-discovery.engine-id", "Discovery Engine App-ID"),
            false,
            engineId != null && !engineId.isBlank()
        ));

        return new SetupProviderDto("GOOGLE_DISCOVERY", "Google Discovery", fields);
    }

    // ---- LLM dimension ----

    private SetupDimensionDto buildLlmDimension(Map<String, String> exampleComments, Map<String, String> configComments) {
        String activeProvider;
        if (llmProperties.getMode() == LlmMode.LOCAL) {
            activeProvider = "LOCAL";
        } else {
            activeProvider = llmProperties.getExternalProvider().name();
        }

        return new SetupDimensionDto(
            "LLM", "Textanalyse-Engine", activeProvider,
            List.of(
                buildGroqProvider(exampleComments, configComments),
                buildOpenRouterProvider(exampleComments, configComments),
                buildLocalProvider(configComments)
            )
        );
    }

    private SetupProviderDto buildGroqProvider(Map<String, String> exampleComments, Map<String, String> configComments) {
        ProviderConfig cfg = providerProperties.getProvider(Provider.GROQ);
        String apiKey = cfg != null ? cfg.getApiKey() : null;

        List<SetupFieldDto> fields = new ArrayList<>();

        fields.add(new SetupFieldDto(
            "api-key",
            null,
            ProviderStatusService.GROQ_PLACEHOLDER,
            exampleComments.getOrDefault("querchecker.api.limits.groq.api-key", "Groq API-Key"),
            true,
            isSecretConfigured(apiKey, ProviderStatusService.GROQ_PLACEHOLDER)
        ));

        String model = cfg != null ? cfg.getModel() : null;
        fields.add(new SetupFieldDto(
            "model",
            model,
            null,
            configComments.getOrDefault("querchecker.api.limits.groq.model", "LLM-Modell für DL-Extraktion + QuickFacts"),
            false,
            model != null && !model.isBlank()
        ));

        String secondary = cfg != null ? cfg.getModelLookupSecondary() : null;
        fields.add(new SetupFieldDto(
            "model-lookup-secondary",
            secondary,
            null,
            configComments.getOrDefault("querchecker.api.limits.groq.model-lookup-secondary", "Sekundäres Modell für Folge-Lookups"),
            false,
            secondary != null && !secondary.isBlank()
        ));

        return new SetupProviderDto("GROQ", "Groq", fields);
    }

    private SetupProviderDto buildOpenRouterProvider(Map<String, String> exampleComments, Map<String, String> configComments) {
        ProviderConfig cfg = providerProperties.getProvider(Provider.OPENROUTER);
        String apiKey = cfg != null ? cfg.getApiKey() : null;

        List<SetupFieldDto> fields = new ArrayList<>();

        fields.add(new SetupFieldDto(
            "api-key",
            null,
            ProviderStatusService.OPENROUTER_PLACEHOLDER,
            exampleComments.getOrDefault("querchecker.api.limits.openrouter.api-key", "OpenRouter API-Key"),
            true,
            isSecretConfigured(apiKey, ProviderStatusService.OPENROUTER_PLACEHOLDER)
        ));

        String orModel = cfg != null ? cfg.getModel() : null;
        fields.add(new SetupFieldDto(
            "model",
            orModel,
            null,
            configComments.getOrDefault("querchecker.api.limits.openrouter.model", "Modellname für OpenRouter"),
            false,
            orModel != null && !orModel.isBlank()
        ));

        return new SetupProviderDto("OPENROUTER", "OpenRouter", fields);
    }

    private SetupProviderDto buildLocalProvider(Map<String, String> configComments) {
        String sourceModel = llmProperties.getLocalSourceModel();
        String gpuLayers = String.valueOf(llmProperties.getGpuLayers());
        return new SetupProviderDto("LOCAL", "Lokales Modell", List.of(
            new SetupFieldDto(
                "local-source-model",
                sourceModel,
                null,
                configComments.getOrDefault("querchecker.llm.local-source-model",
                    "Substring-Match gegen Modellname — bestimmt suggestedTerm"),
                false,
                sourceModel != null && !sourceModel.isBlank()
            ),
            new SetupFieldDto(
                "gpu-layers",
                gpuLayers,
                null,
                configComments.getOrDefault("querchecker.llm.gpu-layers",
                    "0 = CPU-only; 999 = alle Layer auf GPU"),
                false,
                true
            )
        ));
    }

    // ---- Save to server ----

    /**
     * Schreibt secrets.yml und querchecker.yml auf den Server.
     * secrets.yml: Template aus example.yml mit eingesetzten Werten.
     * querchecker.yml: bestehende Datei mit überschriebenen Wizard-Feldern.
     */
    public void saveToServer(ProviderSetupSaveRequest request) throws IOException {
        writeSecretsYml(request.secrets());
        writeQuercheckerYml(request);
        log.info("[ProviderSetup] Konfiguration gespeichert — search={}, llm={}",
            request.searchProvider(), request.llmProvider());

        // Starte Server neu um neue Konfiguration zu laden
        scheduleRestart();
    }

    /** Plant einen Server-Neustart nach kurzer Verzögerung. */
    private void scheduleRestart() {
        Thread restartThread = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            log.info("[ProviderSetup] Starte Server neu…");
            SpringApplication.exit(applicationContext, () -> 0);
            System.exit(0);
        });
        restartThread.setDaemon(false);
        restartThread.setName("provider-setup-restart");
        restartThread.start();
    }

    /**
     * Schreibt secrets.yml — Template aus example.yml mit ersetzten Platzhalter-Werten.
     * Felder ohne Wert im Request behalten den Platzhalter.
     */
    private void writeSecretsYml(Map<String, String> secrets) throws IOException {
        if (!Files.exists(EXAMPLE_YML)) {
            throw new IOException("example.yml nicht gefunden: " + EXAMPLE_YML);
        }
        String template = Files.readString(EXAMPLE_YML, StandardCharsets.UTF_8);

        // Ersetze Platzhalter durch echte Werte
        for (var entry : secrets.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                template = template.replace(entry.getKey(), entry.getValue());
            }
        }

        Path secretsPath = Path.of(SECRETS_YML_PATH);
        Files.writeString(secretsPath, template, StandardCharsets.UTF_8);
        log.info("[ProviderSetup] secrets.yml geschrieben: {}", secretsPath.toAbsolutePath());
    }

    /**
     * Schreibt querchecker.yml — bestehende Datei mit überschriebenen Provider-Feldern.
     * Zeilenweise Ersetzung per Key-Match.
     */
    private void writeQuercheckerYml(ProviderSetupSaveRequest request) throws IOException {
        Path configPath = Path.of(QUERCHECKER_YML_PATH);
        if (!Files.exists(configPath)) {
            throw new IOException("querchecker.yml nicht gefunden: " + configPath);
        }

        List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        List<String> result = new ArrayList<>(lines.size());

        for (String line : lines) {
            String trimmed = line.stripLeading();

            // active-provider für Web Search
            if (trimmed.startsWith("active-provider:") && isInSection(lines, result.size(), "search")) {
                result.add(replaceYamlValue(line, request.searchProvider()));
                continue;
            }

            // external-provider für LLM
            if (trimmed.startsWith("external-provider:") && request.llmProvider() != null) {
                String llm = request.llmProvider();
                if (!"LOCAL".equals(llm)) {
                    result.add(replaceYamlValue(line, llm));
                }
                continue;
            }

            // mode für LLM
            if (trimmed.startsWith("mode:") && isInSection(lines, result.size(), "llm")) {
                String mode = "LOCAL".equals(request.llmProvider()) ? "LOCAL" : "API";
                result.add(replaceYamlValue(line, mode));
                continue;
            }

            // Config-Felder aus dem Request — nur in der aktiven Provider-Sektion
            if (request.config() != null) {
                String llmSection = providerToYamlSection(request.llmProvider());
                String searchSection = providerToYamlSection(request.searchProvider());
                boolean replaced = false;
                for (var entry : request.config().entrySet()) {
                    if (trimmed.startsWith(entry.getKey() + ":") && entry.getValue() != null) {
                        boolean inSection = (llmSection != null && isInSection(lines, result.size(), llmSection))
                            || (searchSection != null && isInSection(lines, result.size(), searchSection));
                        if (inSection) {
                            result.add(replaceYamlValue(line, entry.getValue()));
                            replaced = true;
                            break;
                        }
                    }
                }
                if (replaced) continue;
            }

            result.add(line);
        }

        Files.write(configPath, result, StandardCharsets.UTF_8);
        log.info("[ProviderSetup] querchecker.yml geschrieben: {}", configPath.toAbsolutePath());
    }

    /**
     * Ersetzt den Wert in einer YAML-Zeile (vor dem Kommentar).
     * "    key: old_value # comment" → "    key: new_value # comment"
     */
    private String replaceYamlValue(String line, String newValue) {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return line;

        String prefix = line.substring(0, colonIdx + 1);
        String rest = line.substring(colonIdx + 1);

        // Kommentar erhalten
        int commentIdx = rest.indexOf('#');
        String comment = commentIdx >= 0 ? " " + rest.substring(commentIdx) : "";

        return prefix + " " + newValue + comment;
    }

    /** Heuristic: prüft ob die aktuelle Zeile innerhalb einer bestimmten YAML-Section liegt. */
    private boolean isInSection(List<String> lines, int currentIdx, String sectionKey) {
        for (int i = currentIdx - 1; i >= 0; i--) {
            String trimmed = lines.get(i).stripLeading();
            if (trimmed.startsWith(sectionKey + ":")) return true;
            // Stopp bei einer anderen Top-Level-Section (weniger Indent als aktuelle Zeile)
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") &&
                lines.get(i).length() - trimmed.length() < lines.get(currentIdx).length() - lines.get(currentIdx).stripLeading().length()) {
                break;
            }
        }
        return false;
    }

    // ---- helpers ----

    /**
     * Konvertiert Provider-Enum-Name zum YAML-Section-Key.
     * GROQ → "groq", GOOGLE_DISCOVERY → "google-discovery"
     */
    private String providerToYamlSection(String provider) {
        if (provider == null) return null;
        return provider.toLowerCase().replace('_', '-');
    }

    /** Prüft ob ein Secret-Wert konfiguriert ist (nicht Platzhalter, nicht leer). */
    private boolean isSecretConfigured(String value, String placeholder) {
        return !ProviderStatusService.isUnconfigured(value, placeholder);
    }
}
