package at.querchecker.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response-Body von OpenAI-kompatiblen Chat-Completion-Endpunkten (Groq, OpenRouter).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatResponse {

    private List<Choice> choices = new ArrayList<>();
    private Usage usage;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Message {
            private String content;
        }
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("completion_tokens")
        private int completionTokens;
    }

    /** Liefert den Textinhalt der ersten Choice, oder leeren String wenn keine vorhanden. */
    public String firstChoice() {
        if (choices == null || choices.isEmpty()) return "";
        Choice choice = choices.get(0);
        if (choice.getMessage() == null) return "";
        String content = choice.getMessage().getContent();
        return content != null ? content : "";
    }

    public int getTokensInput() {
        return usage != null ? usage.getPromptTokens() : 0;
    }

    public int getTokensOutput() {
        return usage != null ? usage.getCompletionTokens() : 0;
    }
}
