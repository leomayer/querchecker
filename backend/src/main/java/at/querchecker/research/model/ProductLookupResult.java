package at.querchecker.research.model;

import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.SourceType;
import lombok.Builder;
import lombok.Data;

/**
 * Ergebnis einer ProductLookup-Anfrage — enthält Status, quickFacts und Quellen-Metadaten.
 */
@Data
@Builder
public class ProductLookupResult {

    private LookupStatus status;
    private String quickFactsJson;
    private String featureGroupsJson;
    private String icecatId;
    private SourceType sourceType;
    private String sourceDomain;
    private String siteLabel;
    private String sourceUrl;

    public static ProductLookupResult failed() {
        return ProductLookupResult.builder().status(LookupStatus.FAILED).build();
    }

    public static ProductLookupResult quotaExceeded() {
        return ProductLookupResult.builder().status(LookupStatus.QUOTA_EXCEEDED).build();
    }
}
