package at.querchecker.api.config;

import at.querchecker.api.entity.Provider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Konfiguration für den aktiven Extraction-Provider.
 * Wert: querchecker.api.extraction.active-provider in application.yml
 */
@Component
@ConfigurationProperties(prefix = "querchecker.api.extraction")
@Data
public class ExtractionProperties {

    /** Aktiver LLM-Provider: GROQ | OPENROUTER — Neustart nötig bei Wechsel */
    private Provider activeProvider = Provider.GROQ;
}
