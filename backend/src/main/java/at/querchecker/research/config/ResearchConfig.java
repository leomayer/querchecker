package at.querchecker.research.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "querchecker.research")
@Data
public class ResearchConfig {

    /**
     * Substring matched (case-insensitive) against DlExtractionTerm.modelName
     * to select which model's output becomes the suggested search term.
     * Change value to switch models without any code change.
     * Example values: "llama", "groq", "nuextract"
     */
    private String sourceModel = "llama";
}
