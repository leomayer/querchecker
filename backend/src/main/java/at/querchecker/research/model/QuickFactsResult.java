package at.querchecker.research.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Ergebnis der LLM-Extraktion von technischen Spezifikationen.
 * quickFacts: Key-Value-Map mit extrahierten Specs (z.B. {"cpu": "Core i7", "ram": "16 GB"})
 * sources.icecatId: Icecat-Produkt-ID, falls aus den Brave-URLs erkennbar (nullable)
 * sources.icecatUrl: Vollständige Icecat-URL (nullable)
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuickFactsResult {

    private Map<String, String> quickFacts = new HashMap<>();
    private Sources sources = new Sources();

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sources {
        private String icecatId;
        private String icecatUrl;
    }
}
