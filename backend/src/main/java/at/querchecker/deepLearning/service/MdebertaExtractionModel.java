package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.entity.ItemText;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Component
public class MdebertaExtractionModel extends AbstractExtractionModel {

    private static final String MODEL_NAME = "mdeberta-v3-base-squad2";
    private static final Path MODEL_DIR = Paths.get("src/main/resources/models/mdeberta-v3-base-squad2");

    @Override
    protected Path getModelDir() {
        return MODEL_DIR;
    }

    @Override
    public String getName() {
        return MODEL_NAME;
    }

    /** mDeBERTa-v3 does not use token_type_ids. */
    @Override
    protected boolean useTokenTypeIds() {
        return false;
    }

    @PostConstruct
    public void init() {
        try {
            initTokenizer();
            initSession();
            log.info("Loaded model: {}", MODEL_NAME);
        } catch (Exception e) {
            log.warn("Model {} not available – download model files first", MODEL_NAME, e);
        }
    }

    @Override
    public List<ExtractionResult> extract(ItemText input, String prompt, int maxTokens) {
        if (session == null || tokenizer == null) {
            throw new IllegalStateException("Model " + MODEL_NAME + " not loaded");
        }

        String context = truncate(buildContext(input), maxTokens);
        try {
            return runQaInference(prompt, context);
        } catch (Exception e) {
            throw new RuntimeException("Extraction failed for model " + MODEL_NAME, e);
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (session != null) session.close();
        } catch (Exception e) {
            log.warn("Error closing session for {}", MODEL_NAME, e);
        }
    }
}
