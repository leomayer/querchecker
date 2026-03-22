package at.querchecker.research.model;

import at.querchecker.research.entity.LookupStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Ergebnis einer ProductLookup-Anfrage — enthält Status und ggf. quickFactsJson.
 */
@Data
@AllArgsConstructor
public class ProductLookupResult {

    private LookupStatus status;
    private String quickFactsJson;

    public static ProductLookupResult failed() {
        return new ProductLookupResult(LookupStatus.FAILED, null);
    }

    public static ProductLookupResult quotaExceeded() {
        return new ProductLookupResult(LookupStatus.QUOTA_EXCEEDED, null);
    }
}
