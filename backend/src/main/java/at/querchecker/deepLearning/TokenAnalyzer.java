import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.util.List;

public class TokenAnalyzer {

    private static final int MAX_TOKENS = 128; // Dein gewähltes Modell-Limit

    public void processText(String description) {
        // 1. Tokenizer laden (einmalig für das gewählte Modell, z.B. MiniLM)
        try (
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(
                "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
            )
        ) {
            // 2. Text tokenisieren
            Encoding encoding = tokenizer.encode(description);
            long tokenCount = encoding.getTokens().length;

            System.out.println("Anzahl der Tokens: " + tokenCount);

            // 3. Entscheidung treffen
            if (tokenCount <= MAX_TOKENS) {
                System.out.println("Direkte Verarbeitung startet...");
                //                extractKeywords(description);
            } else {
                System.out.println(
                    "Text zu lang (" +
                        tokenCount +
                        " Tokens). Starte Splitting..."
                );
                // List<String> chunks = splitIntoChunks(description);
                // for (String chunk : chunks) {
                //     extractKeywords(chunk);
                // }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
