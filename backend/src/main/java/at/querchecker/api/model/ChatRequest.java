package at.querchecker.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Request-Body für OpenAI-kompatible Chat-Completion-Endpunkte (Groq, OpenRouter).
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {

    private String model;
    private List<Message> messages;
    private Double temperature;
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;

    @Data
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}
