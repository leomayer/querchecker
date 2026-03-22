package at.querchecker.research.model;

import at.querchecker.research.entity.LookupStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class LookupResponse {
    private LookupStatus lookupStatus;
    private Map<String, String> quickFacts;
    private String icecatId;
    /** Already-cached Icecat full-specs JSON, or null if not yet fetched. */
    private String icecatSpecsJson;
}
