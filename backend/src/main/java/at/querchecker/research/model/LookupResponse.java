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
}
