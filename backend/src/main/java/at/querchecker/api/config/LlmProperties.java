package at.querchecker.api.config;

import at.querchecker.api.entity.Provider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Zentrale LLM-Konfiguration.
 *
 * mode: API  → external-provider bestimmt den Zugang (Groq, OpenRouter).
 *              Der source-model-Substring wird automatisch aus dem Provider-Namen abgeleitet
 *              (GROQ → "groq", OPENROUTER → "openrouter") — kein separater Config-Wert nötig.
 *
 * mode: LOCAL → lokale GGUF-Modelle via llama.cpp; local-source-model und gpu-layers relevant.
 */
@Component
@ConfigurationProperties(prefix = "querchecker.llm")
@Data
public class LlmProperties {

    /** API | LOCAL */
    private LlmMode mode = LlmMode.API;

    /** Nur relevant bei mode: API — GROQ | OPENROUTER */
    private Provider externalProvider = Provider.GROQ;

    /**
     * Substring-Match gegen DlModelConfig.modelName — bestimmt welches DL-Modell
     * den suggestedTerm (Suchfeld-Vorbefüllung) liefert. Gilt in beiden Modi (API + LOCAL).
     * Aktive DL-Modelle: groq, llama-3.2-3b, qwen3-4b, nuextract-1.5
     * Kurzformen: "llama", "groq", "qwen", "nuextract"
     */
    private String localSourceModel = "llama";

    /** Nur relevant bei mode: LOCAL — 0 = CPU-only; 999 = alle Layer auf GPU */
    private int gpuLayers = 0;

    /**
     * Liefert den effektiven Substring für den suggestedTerm-Match.
     * API-Modus:   automatisch aus external-provider abgeleitet (GROQ → "groq")
     * LOCAL-Modus: local-source-model
     */
    public String getEffectiveSourceModel() {
        return mode == LlmMode.API
            ? externalProvider.name().toLowerCase()
            : localSourceModel;
    }
}
