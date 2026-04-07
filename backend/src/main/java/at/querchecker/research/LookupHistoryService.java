package at.querchecker.research;

import at.querchecker.research.entity.ListingLookupHistory;
import at.querchecker.research.model.LookupHistoryEntryDto;
import at.querchecker.research.model.LookupResponse;
import at.querchecker.research.model.ProductLookupResult;
import at.querchecker.research.repository.ListingLookupHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LookupHistoryService {

    private static final int MAX_HISTORY = 5;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ListingLookupHistoryRepository repo;

    public List<LookupHistoryEntryDto> getHistory(Long listingId) {
        return repo.findByListingIdOrderByCreatedAtDesc(listingId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<LookupHistoryEntryDto> saveAndGetHistory(Long listingId, String lookupTerm, ProductLookupResult result) {
        ListingLookupHistory entry = ListingLookupHistory.builder()
                .listingId(listingId)
                .lookupTerm(lookupTerm)
                .lookupStatus(result.getStatus())
                .quickFactsJson(result.getQuickFactsJson())
                .icecatId(result.getIcecatId())
                .sourceType(result.getSourceType())
                .sourceDomain(result.getSourceDomain())
                .siteLabel(result.getSiteLabel())
                .sourceUrl(result.getSourceUrl())
                .featureGroupsJson(result.getFeatureGroupsJson())
                .build();
        repo.save(entry);
        return pruneAndReturn(listingId);
    }

    public List<LookupHistoryEntryDto> saveAndGetHistory(Long listingId, String lookupTerm, LookupResponse response) {
        ListingLookupHistory entry = ListingLookupHistory.builder()
                .listingId(listingId)
                .lookupTerm(lookupTerm)
                .lookupStatus(response.getLookupStatus())
                .quickFactsJson(toJson(response.getQuickFacts()))
                .icecatId(response.getIcecatId())
                .sourceType(response.getSourceType())
                .sourceDomain(response.getSourceDomain())
                .siteLabel(response.getSiteLabel())
                .sourceUrl(response.getSourceUrl())
                .featureGroupsJson(response.getFeatureGroupsJson())
                .build();
        repo.save(entry);
        return pruneAndReturn(listingId);
    }

    private List<LookupHistoryEntryDto> pruneAndReturn(Long listingId) {
        List<ListingLookupHistory> all = repo.findByListingIdOrderByCreatedAtDesc(listingId);
        if (all.size() > MAX_HISTORY) {
            repo.deleteAll(all.subList(MAX_HISTORY, all.size()));
            return all.subList(0, MAX_HISTORY).stream().map(this::toDto).toList();
        }
        return all.stream().map(this::toDto).toList();
    }

    private LookupHistoryEntryDto toDto(ListingLookupHistory h) {
        return new LookupHistoryEntryDto(
                h.getLookupTerm(),
                h.getLookupStatus(),
                parseQuickFacts(h.getQuickFactsJson()),
                h.getIcecatId(),
                h.getSourceType(),
                h.getSourceDomain(),
                h.getSiteLabel(),
                h.getSourceUrl(),
                h.getFeatureGroupsJson(),
                h.getCreatedAt()
        );
    }

    private Map<String, String> parseQuickFacts(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse quickFactsJson in history: {}", e.getMessage());
            return Map.of();
        }
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }
}
