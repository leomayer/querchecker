package at.querchecker.research.model;

import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.SourceType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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
    /** Zeitpunkt ab dem ein erneuter Lookup versucht werden kann (nur bei FAILED/ERROR-Cache-Hit). */
    private LocalDateTime retryAfter;

    public static ProductLookupResult failed() {
        return ProductLookupResult.builder().status(LookupStatus.FAILED).build();
    }

    public static ProductLookupResult quotaExceeded() {
        return ProductLookupResult.builder().status(LookupStatus.QUOTA_EXCEEDED).build();
    }

    public static ProductLookupResult noSources() {
        return ProductLookupResult.builder().status(LookupStatus.NO_SOURCES).build();
    }

    public static ProductLookupResult error() {
        return ProductLookupResult.builder().status(LookupStatus.ERROR).build();
    }
}
