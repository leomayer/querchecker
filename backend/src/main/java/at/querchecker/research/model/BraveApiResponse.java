package at.querchecker.research.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Jackson-DTO für die Antwort der Brave Search Web API.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BraveApiResponse {

    private WebSection web;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebSection {
        private List<WebResult> results = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebResult {
        private String title;
        private String url;
        private String description;

        @JsonProperty("extra_snippets")
        private List<String> extraSnippets = new ArrayList<>();
    }
}
