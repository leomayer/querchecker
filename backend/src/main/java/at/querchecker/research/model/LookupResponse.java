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
    private String sourceUrl;
    private String featureGroupsJson;
}
