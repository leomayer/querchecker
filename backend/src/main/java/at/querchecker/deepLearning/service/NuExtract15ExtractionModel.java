package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.entity.ItemText;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * numind/NuExtract-v1.5 — structured extraction LLM (Qwen2-1.5B fine-tune).
 * 3× larger than tiny (1.5B vs 0.5B), same prompt format, better quality on ambiguous titles.
 * Architecture: qwen2 — fully compatible with llama.cpp 4.x.
 * Download: run backend/src/main/resources/models/download_nuextract15.py
 */
@Slf4j
@Component
public class NuExtract15ExtractionModel extends AbstractLlamaExtractionModel {

    private static final String MODEL_NAME = "nuextract-1.5";
    private static final String MODEL_PATH = "src/main/resources/models/nuextract-1.5/model.gguf";

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
        int end = response.indexOf('"');
        String name = (end >= 0 ? response.substring(0, end) : response).trim();
        if (name.isBlank()) return List.of();
        return List.of(ExtractionResult.builder()
            .term(name)
            .confidence(0.88)
            .build());
    }
}
