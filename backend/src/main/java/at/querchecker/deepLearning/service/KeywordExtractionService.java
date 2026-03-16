package at.querchecker.deepLearning.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class KeywordExtractionService {

    private HuggingFaceTokenizer tokenizerMini;
    private HuggingFaceTokenizer tokenizerLarge;

    private static final int LIMIT_MINI = 128;
    private static final int LIMIT_LARGE = 512;

    public KeywordExtractionService(
        String modelPathMini,
        String modelPathLarge
    ) {
        // Lädt die Tokenizer lokal
        try {
            this.tokenizerMini = HuggingFaceTokenizer.newInstance(
                Paths.get(modelPathMini + "/tokenizer.json")
            );
            this.tokenizerLarge = this.tokenizerMini;
            // this.tokenizerLarge = HuggingFaceTokenizer.newInstance(
            //     Paths.get(modelPathLarge + "/tokenizer.json")
            // );
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public List<String> processText(String input) {
        long countMini = tokenizerMini.encode(input).getTokens().length;
        long countLarge = tokenizerLarge.encode(input).getTokens().length;

        System.out.println("Tokens Mini (128): " + countMini);
        System.out.println("Tokens Large (512): " + countLarge);

        // Hier nutzen wir das größte Fenster als Basis für das Splitting
        List<String> chunks = (countLarge > LIMIT_LARGE)
            ? splitText(input, 1500)
            : List.of(input);

        // In der echten Applikation würdest du hier die Chunks an das DJL-Modell senden
        // Für dieses Beispiel simulieren wir die Rückgabe der Keywords
        return chunks
            .stream()
            .map(
                chunk ->
                    "Schlagwort_aus_" +
                    chunk.substring(0, Math.min(10, chunk.length()))
            )
            .collect(Collectors.toList());
    }

    private List<String> splitText(String text, int charLimit) {
        List<String> chunks = new ArrayList<>();
        // Einfacher Split an Leerzeichen zur Demonstration
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + charLimit, text.length());
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
