package at.querchecker.deepLearning.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.entity.ItemText;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractExtractionModel implements ExtractionModel {

    protected volatile HuggingFaceTokenizer tokenizer;
    protected volatile OrtSession session;
    protected volatile OrtEnvironment env;

    protected abstract Path getModelDir();

    protected void initTokenizer() throws IOException {
        this.tokenizer = HuggingFaceTokenizer.newInstance(
            getModelDir().resolve("tokenizer.json"));
    }

    protected void initSession() throws Exception {
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(
            getModelDir().toAbsolutePath().resolve("model.onnx").toString());
    }

    protected String buildContext(ItemText input) {
        return "Titel: " + input.getTitle() + "\n\n" + input.getDescription();
    }

    protected String truncate(String text, int maxTokens) {
        if (tokenizer == null) {
            return text;
        }
        Encoding encoding = tokenizer.encode(text);
        long[] ids = encoding.getIds();
        if (ids.length <= maxTokens) {
            return text;
        }
        long[] truncatedIds = new long[maxTokens];
        System.arraycopy(ids, 0, truncatedIds, 0, maxTokens);
        return tokenizer.decode(truncatedIds);
    }

    /**
     * Override to false for models that do not accept token_type_ids (e.g. DeBERTa-v3).
     */
    protected boolean useTokenTypeIds() {
        return true;
    }

    protected List<ExtractionResult> runQaInference(String question, String context) throws Exception {
        log.debug("runQaInference: question='{}', context length={}", question, context.length());
        Encoding encoding = tokenizer.encode(question, context);
        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] tokenTypeIds = encoding.getTypeIds();
        log.debug("Tokenized: {} tokens, typeIds unique values={}",
            inputIds.length, Arrays.stream(tokenTypeIds).distinct().toArray());

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(env, new long[][]{inputIds}));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, new long[][]{attentionMask}));
            if (useTokenTypeIds()) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(env, new long[][]{tokenTypeIds}));
            }

            try (OrtSession.Result result = session.run(inputs)) {
                float[][] startLogits = (float[][]) result.get(0).getValue();
                float[][] endLogits = (float[][]) result.get(1).getValue();

                // Find context token range — try tokenTypeIds first, fall back to [SEP] positions.
                // Some models (multilingual BERT) return all-zero typeIds.
                long sepId = findSepTokenId(inputIds);
                int contextStart = -1;
                int contextEnd = -1;
                for (int i = 0; i < tokenTypeIds.length; i++) {
                    if (tokenTypeIds[i] == 1) {
                        if (contextStart == -1) contextStart = i;
                        contextEnd = i;
                    }
                }
                // Exclude trailing [SEP] — it gets tokenTypeId=1 but is not a content token
                if (contextEnd >= 0 && inputIds[contextEnd] == sepId) {
                    contextEnd--;
                }

                // Fallback: find context between second [SEP] (or first [SEP]+1) and last token
                if (contextStart == -1) {
                    log.debug("tokenTypeIds all zero — using [SEP] fallback (sepId={})", sepId);
                    int sepCount = 0;
                    for (int i = 1; i < inputIds.length; i++) {
                        if (inputIds[i] == sepId) {
                            sepCount++;
                            if (sepCount == 1) {
                                // Context starts after first [SEP]
                                contextStart = i + 1;
                            } else if (sepCount == 2) {
                                // Context ends before second [SEP]
                                contextEnd = i - 1;
                                break;
                            }
                        }
                    }
                    // If only one [SEP] found, context goes to end of attention mask
                    if (contextStart != -1 && contextEnd == -1) {
                        for (int i = inputIds.length - 1; i >= contextStart; i--) {
                            if (attentionMask[i] == 1 && inputIds[i] != sepId) {
                                contextEnd = i;
                                break;
                            }
                        }
                    }
                }

                log.debug("Context range: contextStart={}, contextEnd={}", contextStart, contextEnd);

                // No context range found — model can't answer
                if (contextStart == -1 || contextEnd == -1 || contextStart > contextEnd) {
                    log.debug("No valid context range — returning empty");
                    return List.of();
                }

                int startIdx = argmaxRange(startLogits[0], contextStart, contextEnd);
                int endIdx = argmaxRange(endLogits[0], startIdx, contextEnd);
                log.debug("Answer span: startIdx={}, endIdx={}", startIdx, endIdx);

                // Decode answer span from token IDs
                long[] answerIds = new long[endIdx - startIdx + 1];
                System.arraycopy(inputIds, startIdx, answerIds, 0, answerIds.length);
                String answer = tokenizer.decode(answerIds).trim();

                // Confidence from softmax of start/end logits
                double confidence = softmaxScore(startLogits[0], startIdx)
                    * softmaxScore(endLogits[0], endIdx);

                log.debug("Decoded answer='{}', confidence={}", answer, confidence);

                if (answer.isEmpty() || answer.equals("[CLS]") || answer.equals("[SEP]")) {
                    log.debug("Filtered out special token answer");
                    return List.of();
                }

                return List.of(ExtractionResult.builder()
                    .term(answer)
                    .confidence(confidence)
                    .build());
            }
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    private long findSepTokenId(long[] inputIds) {
        // [SEP] is typically the last non-padding token in a single-sequence encoding,
        // or we can encode it directly. Use a simple heuristic: encode "[SEP]" and take last token.
        Encoding sepEncoding = tokenizer.encode("[SEP]");
        long[] sepIds = sepEncoding.getIds();
        // The actual [SEP] ID is typically the last token (after [CLS] ... [SEP])
        return sepIds[sepIds.length - 1];
    }

    private int argmaxRange(float[] arr, int from, int to) {
        int maxIdx = from;
        for (int i = from + 1; i <= to; i++) {
            if (arr[i] > arr[maxIdx]) {
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    private double softmaxScore(float[] logits, int idx) {
        double max = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            if (v > max) max = v;
        }
        double sumExp = 0;
        for (float v : logits) {
            sumExp += Math.exp(v - max);
        }
        return Math.exp(logits[idx] - max) / sumExp;
    }
}
