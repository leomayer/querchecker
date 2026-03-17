package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.entity.ItemText;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Qwen/Qwen2.5-3B-Instruct — instruction-following LLM for product name extraction.
 * Uses ChatML format. Architecture: qwen2 (supported by llama.cpp 4.x).
 * Download: run backend/src/main/resources/models/download_qwen25.py
 *
 * Note: Qwen3-4B was originally planned here but requires llama.cpp with qwen3 arch support.
 * Qwen2.5-3B-Instruct is used as a replacement until de.kherud:llama ships a newer build.
 */
@Slf4j
@Component
public class Qwen25ExtractionModel extends AbstractLlamaExtractionModel {

    private static final String MODEL_NAME = "qwen2.5-3b";
    private static final String MODEL_PATH = "src/main/resources/models/qwen2.5-3b/model.gguf";

    @Override
    public String getName() {
        return MODEL_NAME;
    }

    @Override
    protected String getModelPath() {
        return MODEL_PATH;
    }

    @PostConstruct
    public void init() {
        try {
            initModel();
            if (llamaModel != null) {
                log.info("Model ready: {}", MODEL_NAME);
            }
        } catch (Throwable e) {
            log.error("Model {} failed to initialize — extraction will be unavailable", MODEL_NAME, e);
        }
    }

    @PreDestroy
    public void close() {
        closeModel();
    }

    @Override
    protected String buildPrompt(ItemText input, String question) {
        return "<|im_start|>system\n"
            + "You extract product names from German classified listings. "
            + "Return ONLY valid JSON with no other text: {\"term\": \"product name\", \"confidence\": 0.85}\n"
            + "<|im_end|>\n"
            + "<|im_start|>user\n"
            + "Extract the product name from this listing. Return ONLY JSON.\n\n"
            + buildContext(input) + "\n"
            + "<|im_end|>\n"
            + "<|im_start|>assistant\n";
    }

    @Override
    protected List<ExtractionResult> parseResponse(String response) {
        JsonNode json = parseJson(response);
        if (json == null) return List.of();

        JsonNode term = json.get("term");
        JsonNode confidence = json.get("confidence");
        if (term == null || term.asText().isBlank()) return List.of();

        double conf = confidence != null ? confidence.asDouble(0.75) : 0.75;
        return List.of(ExtractionResult.builder()
            .term(term.asText().trim())
            .confidence(conf)
            .build());
    }
}
