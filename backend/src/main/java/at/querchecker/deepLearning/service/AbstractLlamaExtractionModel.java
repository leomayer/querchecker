package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.entity.ItemText;
import at.querchecker.service.AppConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Base class for GGUF-based generative extraction models (llama.cpp via java-llama.cpp).
 * Parallel to AbstractExtractionModel (ONNX QA), not a subclass.
 */
@Slf4j
public abstract class AbstractLlamaExtractionModel implements ExtractionModel {

    protected volatile LlamaModel llamaModel;

    @Autowired
    private AppConfigService appConfigService;

    @Value("${querchecker.dl.gpu-layers:0}")
    private int gpuLayers;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Absolute or workspace-relative path to the model.gguf file. */
    protected abstract String getModelPath();

    /** Build the full prompt string for this model's format. */
    protected abstract String buildPrompt(ItemText input, String question);

    /** Parse the raw generation output into extraction results. */
    protected abstract List<ExtractionResult> parseResponse(String response);

    /** Approximate chars per token for German/mixed text with Qwen tokenizer. */
    private static final float CHARS_PER_TOKEN = 3.5f;

    protected int getContextMaxTokens() {
        return appConfigService.getDlContextMaxTokens();
    }

    protected String buildContext(ItemText input) {
        return truncateContext("Titel: " + input.getTitle() + "\n\n" + input.getDescription(), getContextMaxTokens());
    }

    /**
     * Truncates text to an approximate token limit using a character-based estimate.
     * Exact tokenization is not needed — product names always appear early in listings.
     */
    protected String truncateContext(String text, int maxTokens) {
        int maxChars = (int) (maxTokens * CHARS_PER_TOKEN);
        if (text.length() <= maxChars) return text;
        // Cut at last whitespace to avoid splitting mid-word
        int cut = text.lastIndexOf(' ', maxChars);
        return text.substring(0, cut > 0 ? cut : maxChars);
    }

    /** Tokens that signal end of generation. Override for model-specific stop tokens. */
    protected String[] getStopStrings() {
        return new String[]{"<|im_end|>", "<|endoftext|>", "<|end|>"};
    }

    protected void initModel() {
        java.nio.file.Path resolved = Paths.get(getModelPath()).toAbsolutePath();
        String modelPath = resolved.toString();
        if (!Files.exists(resolved)) {
            log.warn("Model file not found at {} — run the download script first", modelPath);
            return;
        }
        log.info("Loading GGUF model from {}", modelPath);
        try {
            int threads = Runtime.getRuntime().availableProcessors() / 2;
            ModelParameters params = new ModelParameters()
                .setModel(modelPath)
                .setGpuLayers(gpuLayers)
                .setCtxSize(1536)                   // covers UI max (1024 context) + 128 generation + ~80 prompt overhead
                .setBatchSize(1024)                  // process full prompt in one pass → faster prompt eval
                .setThreads(threads)                 // physical core count — better than logical for CPU inference
                .setThreadsBatch(threads);
            llamaModel = new LlamaModel(params);
            log.info("Loaded GGUF model: {}", modelPath);
        } catch (Throwable e) {
            log.error("Failed to load GGUF model {}", modelPath, e);
        }
    }

    protected String generate(String prompt, int maxTokens) {
        InferenceParameters params = new InferenceParameters(prompt)
            .setTemperature(0.0f)
            .setRepeatPenalty(1.1f)
            .setNPredict(maxTokens)
            .setStopStrings(getStopStrings());
        StringBuilder sb = new StringBuilder();
        for (LlamaOutput output : llamaModel.generate(params)) {
            sb.append(output.text);
        }
        return sb.toString().trim();
    }

    /** Extracts the first complete JSON object from potentially noisy output. */
    protected JsonNode parseJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || start >= end) {
            log.debug("No JSON object found in response: {}", text);
            return null;
        }
        try {
            return MAPPER.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            log.debug("JSON parse failed for: {}", text);
            return null;
        }
    }

    @Override
    public List<ExtractionResult> extract(ItemText input, String question, int maxTokens) {
        if (llamaModel == null) {
            throw new IllegalStateException("Model " + getName() + " not loaded — run download script first");
        }
        String prompt = buildPrompt(input, question);
        String response = generate(prompt, maxTokens);
        log.debug("Model {} raw response: {}", getName(), response);
        return parseResponse(response);
    }

    protected void closeModel() {
        try {
            if (llamaModel != null) llamaModel.close();
        } catch (Exception e) {
            log.warn("Error closing model {}", getName(), e);
        }
    }
}
