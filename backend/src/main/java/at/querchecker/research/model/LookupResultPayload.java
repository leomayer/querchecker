package at.querchecker.research.model;

import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.SourceType;

import java.util.Map;

/**
 * SSE event payload for the "lookup-result" event.
 * Pushed when a rate-limited lookup completes its backend retry.
 */
public record LookupResultPayload(
        Long listingId,
        LookupStatus lookupStatus,
        Map<String, String> quickFacts,
        String icecatId,
        SourceType sourceType,
        String sourceDomain,
        String siteLabel,
        String sourceUrl,
        String featureGroupsJson,
        String retryProvider,
        String retryModel
) {}
