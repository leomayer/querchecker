package at.querchecker.research;

import at.querchecker.research.entity.ExtractionQuality;
import at.querchecker.research.entity.SourceType;
import at.querchecker.research.model.QuickFactsResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExtractionQualityEvaluator {

    /**
     * Bewertet die Qualität eines LLM-Extraktionsergebnisses.
     *
     * @param result          LLM-Output (quickFacts + sources)
     * @param systemFields    SYSTEM-Pflichtfelder aus {@code getMandatorySystemFields()} —
     *                        NUR SYSTEM-Felder (cpu, ram, panel_type), da USER-Keywords
     *                        (oled, thunderbolt4) keine quickFacts-Keys sind und die
     *                        Abdeckungsberechnung verfälschen würden
     * @param sourceType      Quelle — beeinflusst icecatId-Prüfung
     */
    public ExtractionQuality evaluate(
            QuickFactsResult result,
            List<String> systemFields,
            SourceType sourceType) {

        if (result == null || result.getQuickFacts() == null
                || result.getQuickFacts().isEmpty()) {
            return systemFields.isEmpty()
                    ? ExtractionQuality.FAILED_NO_CRITERIA
                    : ExtractionQuality.EMPTY;
        }

        // Keine Pflichtfelder → GOOD wenn überhaupt etwas extrahiert wurde
        if (systemFields.isEmpty()) {
            return ExtractionQuality.GOOD;
        }

        long found = systemFields.stream()
                .filter(f -> result.getQuickFacts().containsKey(f))
                .count();
        double coverage = (double) found / systemFields.size();

        // Bei ICECAT: fehlende icecatId → PARTIAL (Full-Specs-Button wäre tot)
        boolean icecatIdMissing = sourceType == SourceType.ICECAT
                && result.getSources().getIcecatId() == null;

        if (coverage >= 0.6 && !icecatIdMissing) return ExtractionQuality.GOOD;
        if (coverage >= 0.3 || !result.getQuickFacts().isEmpty()) return ExtractionQuality.PARTIAL;
        return ExtractionQuality.EMPTY;
    }
}
