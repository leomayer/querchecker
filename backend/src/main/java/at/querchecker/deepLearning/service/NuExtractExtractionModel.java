package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.entity.ItemText;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * numind/NuExtract-v1.5-tiny — structured extraction LLM (Qwen2-0.5B fine-tune).
 * Architecture: qwen2 — fully compatible with llama.cpp 4.x raw prompting.
 * Download: run backend/src/main/resources/models/download_nuextract.py
 */
@Slf4j
@Component
public class NuExtractExtractionModel extends AbstractLlamaExtractionModel {

    private static final String MODEL_NAME = "nuextract-1.5-tiny";
    private static final String MODEL_PATH = "src/main/resources/models/nuextract-1.5-tiny/model.gguf";

    // Response priming — model completes the product name value.
    // Stop strings prevent the model from hallucinating extra JSON fields:
    //   "\", \""  stops at the field boundary (", "next_key")
    //   "\"}"     stops when the JSON object closes correctly
    private static final String PRIME = "{\"product_name\": \"";
    private static final String[] STOP_STRINGS = {
        "<|im_end|>", "<|endoftext|>", "<|end|>", "\", \"", "\"}"
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

    /**
     * NuExtract-1.5 prompt format: no system message, ### Template / ### Context.
     * Response priming forces the model to complete the JSON value directly.
     */
    @Override
    protected String buildPrompt(ItemText input, String question) {
        return "<|im_start|>user\n"
            + "### Template:\n"
            + "{\"product_name\": \"\"}\n\n"
            + "### Context:\n"
            + truncateContext("Title: " + input.getTitle() + "\n\n" + input.getDescription(), getContextMaxTokens()) + "\n"
            + "<|im_end|>\n"
            + "<|im_start|>assistant\n"
            + PRIME;
    }

    @Override
    protected String[] getStopStrings() {
        return STOP_STRINGS;
    }

    @Override
    protected List<ExtractionResult> parseResponse(String response) {
        // The response IS the product name value — the model continues from the primed prefix.
        // Stop strings cut off any extra hallucinated fields before they accumulate.
        // Take everything up to the first closing quote (just in case a stop string was missed).
        int end = response.indexOf('"');
        String name = (end >= 0 ? response.substring(0, end) : response).trim();
        if (name.isBlank()) return List.of();
        return List.of(ExtractionResult.builder()
            .term(name)
            .confidence(0.85)
            .build());
    }
}
