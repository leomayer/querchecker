package at.querchecker.research;

import at.querchecker.research.model.QuickFactsResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static at.querchecker.research.entity.ExtractionQuality.*;
import static at.querchecker.research.entity.SourceType.*;
import static org.assertj.core.api.Assertions.assertThat;

class ExtractionQualityEvaluatorTest {

    ExtractionQualityEvaluator evaluator = new ExtractionQualityEvaluator();

    @Test
    void evaluate_returnsGood_whenAllMandatoryPresent_andIcecatIdSet() {
        QuickFactsResult result = result(
                Map.of("cpu", "i7", "ram", "16GB", "display", "14\""), "12345");

        assertThat(evaluator.evaluate(result, List.of("cpu", "ram", "display"), ICECAT))
                .isEqualTo(GOOD);
    }

    @Test
    void evaluate_returnsPartial_whenIcecatIdMissing_forIcecatSource() {
        QuickFactsResult result = result(
                Map.of("cpu", "i7", "ram", "16GB", "display", "14\""), null);

        assertThat(evaluator.evaluate(result, List.of("cpu", "ram", "display"), ICECAT))
                .isEqualTo(PARTIAL);
    }

    @Test
    void evaluate_returnsGood_whenIcecatIdMissing_forNonIcecatSource() {
        QuickFactsResult result = result(
                Map.of("panel_type", "OLED", "resolution", "4K", "screen_size", "55\""), null);

        assertThat(evaluator.evaluate(result, List.of("panel_type", "resolution"), FLATPANELSHD))
                .isEqualTo(GOOD);
    }

    @Test
    void evaluate_returnsPartial_whenCoverageBelowThreshold() {
        // 1 von 3 Feldern = 33% < 60%
        QuickFactsResult result = result(Map.of("cpu", "i7"), "12345");

        assertThat(evaluator.evaluate(result, List.of("cpu", "ram", "display"), ICECAT))
                .isEqualTo(PARTIAL);
    }

    @Test
    void evaluate_userFieldsNotInSystemFields_noImpactOnCoverage() {
        // USER-Keywords (oled, thunderbolt4) kommen NICHT in systemFields —
        // nur SYSTEM-Felder (cpu, ram) werden für die Abdeckung geprüft.
        // LLM extrahiert korrekt cpu+ram; "oled" erscheint als panel_type-Value, nicht als Key.
        QuickFactsResult result = result(
                Map.of("cpu", "Core i7", "ram", "16 GB", "panel_type", "OLED"), "42");

        assertThat(evaluator.evaluate(result, List.of("cpu", "ram"), ICECAT))
                .isEqualTo(GOOD);
    }

    @Test
    void evaluate_returnsEmpty_whenQuickFactsEmpty_withMandatoryFields() {
        QuickFactsResult result = result(Map.of(), null);

        assertThat(evaluator.evaluate(result, List.of("cpu", "ram"), ICECAT))
                .isEqualTo(EMPTY);
    }

    @Test
    void evaluate_returnsFailedNoCriteria_whenNoMandatoryFields_andEmpty() {
        QuickFactsResult result = result(Map.of(), null);

        assertThat(evaluator.evaluate(result, List.of(), ICECAT))
                .isEqualTo(FAILED_NO_CRITERIA);
    }

    @Test
    void evaluate_returnsGood_whenNoMandatoryFields_andDataPresent() {
        // Kategorien ohne SYSTEM-Felder (z.B. Kabel & Adapter) → GOOD sobald etwas extrahiert
        QuickFactsResult result = result(Map.of("connector", "USB-C"), null);

        assertThat(evaluator.evaluate(result, List.of(), ICECAT))
                .isEqualTo(GOOD);
    }

    @Test
    void evaluate_returnsEmpty_whenResultIsNull() {
        assertThat(evaluator.evaluate(null, List.of("cpu"), ICECAT))
                .isEqualTo(EMPTY);
    }

    private QuickFactsResult result(Map<String, String> facts, String icecatId) {
        QuickFactsResult r = new QuickFactsResult();
        r.setQuickFacts(facts != null ? new HashMap<>(facts) : new HashMap<>());
        r.getSources().setIcecatId(icecatId);
        return r;
    }
}
