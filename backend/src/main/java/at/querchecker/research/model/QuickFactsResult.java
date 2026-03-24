package at.querchecker.research.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM-Extraktionsergebnis: technische Specs + Quellenangaben + optionale Feature-Gruppen (HTML-Fetch-Pfad).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuickFactsResult {

    private Map<String, String> quickFacts = new HashMap<>();
    private List<FeatureGroup> featureGroups;
    private Sources sources = new Sources();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sources {
        private String icecatId;
        private String sourceUrl;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeatureGroup {
        private String name;
        private List<Feature> features;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Feature {
        private String name;
        private String value;
    }
}
