package at.querchecker.deepLearning;

import static org.junit.jupiter.api.Assertions.*;

import at.querchecker.deepLearning.service.KeywordExtractionService;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordExtractionServiceTest {

    @Test
    void testTokenCountingAndSplitting() {
        // Pfade zu deinen heruntergeladenen Modellen
        String miniPath = "src/main/resources/models/paraphrase-multilingual-MiniLM-L12-v2";
        // String largePath = "src/main/resources/models/distiluse";

        KeywordExtractionService service = new KeywordExtractionService(
            miniPath,
            miniPath
            // largePath
        );

        String testText =
            "Das ist ein langer willhaben Text für eine Mountainbike Anzeige aus Wien...";

        List<String> results = service.processText(testText);

        assertNotNull(results);
        assertFalse(results.isEmpty());
        System.out.println("Test erfolgreich. Ergebnisse: " + results);
    }
}
