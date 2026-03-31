package at.querchecker.research.model;

import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.SourceType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class LookupResponse {
    private LookupStatus lookupStatus;
    private Map<String, String> quickFacts;
    private String icecatId;
    /** Already-cached Icecat full-specs JSON, or null if not yet fetched. */
    private String icecatSpecsJson;
    private SourceType sourceType;
    private String sourceDomain;
    private String siteLabel;
    private String sourceUrl;
    private String featureGroupsJson;
    /** ISO datetime ab dem ein erneuter Lookup möglich ist (nur bei FAILED/ERROR). */
    private String retryAfter;
    /** Sekunden bis zum nächsten Retry (nur bei RATE_LIMITED). Ergebnis kommt danach via SSE lookup-result. */
    private Integer retryAfterSeconds;
    /** Provider-Anzeigename (nur bei RATE_LIMITED), z.B. "Groq". */
    private String retryProvider;
    /** Modellname (nur bei RATE_LIMITED), z.B. "llama-3.1-8b-instant". */
    private String retryModel;
}
