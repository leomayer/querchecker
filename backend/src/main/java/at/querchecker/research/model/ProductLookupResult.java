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
    /** Sekunden bis zum nächsten Retry (nur bei RATE_LIMITED). */
    private Integer retryAfterSeconds;
    /** Provider-Anzeigename (nur bei RATE_LIMITED), z.B. "Groq". */
    private String retryProvider;
    /** Modellname (nur bei RATE_LIMITED), z.B. "llama-3.1-8b-instant". */
    private String retryModel;

    public static ProductLookupResult rateLimited(int retryAfterSeconds, String retryProvider, String retryModel) {
        return ProductLookupResult.builder()
                .status(LookupStatus.RATE_LIMITED)
                .retryAfterSeconds(retryAfterSeconds)
                .retryProvider(retryProvider)
                .retryModel(retryModel)
                .build();
    }

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
