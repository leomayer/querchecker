package at.querchecker.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Request-Body für OpenAI-kompatible Chat-Completion-Endpunkte (Groq, OpenRouter).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {

    private String model;
    private List<Message> messages;
    private Double temperature;
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;
    @JsonProperty("response_format")
    private Map<String, String> responseFormat;

    public ChatRequest(String model, List<Message> messages, Double temperature, Integer maxCompletionTokens) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public ChatRequest(String model, List<Message> messages, Double temperature, Integer maxCompletionTokens, Map<String, String> responseFormat) {
        this(model, messages, temperature, maxCompletionTokens);
        this.responseFormat = responseFormat;
    }

    @Data
    public static class Message {
        private final String role;
        private final String content;
    }
}
