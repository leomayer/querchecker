package at.querchecker.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Request-Body für OpenAI-kompatible Chat-Completion-Endpunkte (Groq, OpenRouter).
 */
@Data
@AllArgsConstructor
public class ChatRequest {

    private String model;
    private List<Message> messages;

    @Data
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}
