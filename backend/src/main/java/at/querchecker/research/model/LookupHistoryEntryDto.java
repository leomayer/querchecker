package at.querchecker.research.model;

import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.SourceType;

import java.time.LocalDateTime;
import java.util.Map;

public record LookupHistoryEntryDto(
        String lookupTerm,
        LookupStatus lookupStatus,
        Map<String, String> quickFacts,
        String icecatId,
        SourceType sourceType,
        String sourceDomain,
        String siteLabel,
        String sourceUrl,
        String featureGroupsJson,
        LocalDateTime createdAt
) {}
