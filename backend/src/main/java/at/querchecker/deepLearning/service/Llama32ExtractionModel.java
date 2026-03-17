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
 * meta-llama/Llama-3.2-3B-Instruct — instruction-following LLM for product name extraction.
 * Uses Llama-3 header prompt format. Architecture: llama — supported by llama.cpp 4.x.
 * Download: run backend/src/main/resources/models/download_llama32.py
 */
@Slf4j
@Component
public class Llama32ExtractionModel extends AbstractLlamaExtractionModel {

    private static final String MODEL_NAME = "llama-3.2-3b";
    private static final String MODEL_PATH = "src/main/resources/models/llama-3.2-3b/model.gguf";

    private static final String[] STOP_STRINGS = {
        "<|eot_id|>", "<|end_of_text|>", "<|start_header_id|>"
    };

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
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n"
            + "You extract product names from German classified listings. "
            + "Return ONLY valid JSON with no other text: {\"term\": \"product name\", \"confidence\": 0.85}\n"
            + "<|eot_id|><|start_header_id|>user<|end_header_id|>\n"
            + "Extract the product name from this listing. Return ONLY JSON.\n\n"
            + buildContext(input) + "\n"
            + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n";
    }

    @Override
    protected String[] getStopStrings() {
        return STOP_STRINGS;
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
