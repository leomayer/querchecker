package at.querchecker.deepLearning;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DlExtractionCompletedEvent {
    private final Long itemTextId;
    private final String modelName;
    /**
     * String, nicht {@link ExtractionStatus} — Transportwert für SSE, entkoppelt von der
     * persistierten Postgres-Enum. Erlaubt transiente Werte wie "RATE_LIMITED" oder
     * "EXTRACTION_QUOTA_EXCEEDED" (Konzept Kap. 4, Ebene-2b), die nie in der DB landen —
     * der persistierte {@code DlExtractionRun.status} bleibt in diesen Fällen CANCELLED.
     */
    private final String modelStatus;
}
