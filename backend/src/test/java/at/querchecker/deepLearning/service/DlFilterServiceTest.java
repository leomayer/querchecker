package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.config.DlConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DlFilterServiceTest {

    DlFilterService service = new DlFilterService();

    private DlConfig configWith(int topK, double minConfidence) {
        DlConfig c = new DlConfig();
        c.setTopK(topK);
        c.setMinConfidence(minConfidence);
        return c;
    }

    private ExtractionResult result(String term, double confidence) {
        return ExtractionResult.builder().term(term).confidence(confidence).build();
    }

    @Test
    void filter_sortsByConfidenceDesc() {
        List<ExtractionResult> raw = List.of(
            result("A", 0.5), result("B", 0.9), result("C", 0.7));

        List<ExtractionResult> filtered = service.filter(raw, configWith(10, 0.0));

        assertThat(filtered).extracting(ExtractionResult::getTerm)
            .containsExactly("B", "C", "A");
    }

    @Test
    void filter_respectsTopK() {
        List<ExtractionResult> raw = List.of(
            result("A", 0.9), result("B", 0.8), result("C", 0.7));

        assertThat(service.filter(raw, configWith(2, 0.0))).hasSize(2);
    }

    @Test
    void filter_respectsMinConfidence() {
        List<ExtractionResult> raw = List.of(
            result("A", 0.9), result("B", 0.4), result("C", 0.7));

        List<ExtractionResult> filtered = service.filter(raw, configWith(10, 0.6));

        assertThat(filtered).extracting(ExtractionResult::getTerm)
            .containsExactlyInAnyOrder("A", "C");
    }

    @Test
    void filter_returnsEmpty_whenAllBelowMinConfidence() {
        List<ExtractionResult> raw = List.of(result("A", 0.2), result("B", 0.3));

        assertThat(service.filter(raw, configWith(10, 0.8))).isEmpty();
    }
}
