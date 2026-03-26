package at.querchecker.research;

import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.research.entity.ProductLookup;
import at.querchecker.research.model.FullSpecsRequest;
import at.querchecker.research.model.FullSpecsResponse;
import at.querchecker.research.model.IcecatFetchResult;
import at.querchecker.research.model.LookupRequest;
import at.querchecker.research.model.LookupResponse;
import at.querchecker.research.model.ProductLookupResult;
import at.querchecker.research.repository.ProductLookupRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/listings/{id}")
@RequiredArgsConstructor
@Tag(name = "ProductLookup", description = "Spec-Lookup und Icecat-Vollspezifikation")
public class ProductLookupController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WhListingRepository listingRepository;
    private final ProductLookupRepository productLookupRepository;
    private final ProductLookupService productLookupService;
    private final IcecatService icecatService;

    @PostMapping("/lookup")
    @Operation(summary = "Quick-Facts für ein Inserat per Brave+LLM ermitteln")
    public LookupResponse lookup(@PathVariable Long id, @RequestBody LookupRequest req) {
        log.info("=== LOOKUP START: listingId={}, term=\"{}\" ===", id, req.getLookupTerm());

        WhListing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + id));
        log.info("Listing found: id={}, categoryId={}, categoryName={}", listing.getId(), listing.getWhCategory().getId(), listing.getWhCategory().getName());

        ProductLookupResult result = productLookupService.lookup(req.getLookupTerm(), listing.getWhCategory());
        log.info("Lookup result: status={}, quickFacts={}, sourceType={}, sourceDomain={}",
                result.getStatus(), result.getQuickFactsJson(), result.getSourceType(), result.getSourceDomain());

        ProductLookup saved = productLookupRepository.findByLookupTerm(req.getLookupTerm()).orElse(null);
        String icecatId = result.getIcecatId() != null ? result.getIcecatId()
                : (saved != null ? saved.getIcecatId() : null);
        String icecatSpecsJson = saved != null ? saved.getIcecatSpecsJson() : null;

        LookupResponse response = LookupResponse.builder()
                .lookupStatus(result.getStatus())
                .quickFacts(parseQuickFacts(result.getQuickFactsJson()))
                .icecatId(icecatId)
                .icecatSpecsJson(icecatSpecsJson)
                .sourceType(result.getSourceType())
                .sourceDomain(result.getSourceDomain())
                .sourceUrl(result.getSourceUrl())
                .featureGroupsJson(result.getFeatureGroupsJson())
                .build();

        log.info("=== LOOKUP END: status={}, quickFactsCount={} ===", response.getLookupStatus(), response.getQuickFacts().size());
        return response;
    }

    @PostMapping("/lookup/full-specs")
    @Operation(summary = "Vollständige Icecat-Spezifikation laden und cachen")
    public FullSpecsResponse fullSpecs(@PathVariable Long id, @RequestBody FullSpecsRequest req) {
        ProductLookup lookup = productLookupRepository.findByIcecatId(req.getIcecatId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Kein ProductLookup für icecatId: " + req.getIcecatId()));

        // Return cached specs immediately — no Icecat API call needed.
        if (lookup.getIcecatSpecsJson() != null) {
            log.debug("Returning cached Icecat specs for icecatId={}", req.getIcecatId());
            return new FullSpecsResponse(lookup.getIcecatSpecsJson());
        }

        IcecatFetchResult fetch = icecatService.fetchFullSpecs(req.getIcecatId());

        if (fetch.isNotFound()) {
            // Icecat reported 404 → the stored icecatId is invalid (e.g. extracted from a file URL).
            // Clear it so this bad ID is never retried.
            log.warn("Icecat returned 404 for icecatId={} — clearing from ProductLookup", req.getIcecatId());
            lookup.setIcecatId(null);
            lookup.setIcecatUrl(null);
            productLookupRepository.save(lookup);
            return new FullSpecsResponse(null);
        }

        if (fetch.json() != null) {
            lookup.setIcecatSpecsJson(fetch.json());
            lookup.setIcecatFetchedAt(LocalDateTime.now());
            productLookupRepository.save(lookup);
        }

        return new FullSpecsResponse(fetch.json());
    }

    // --- private helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, String> parseQuickFacts(String quickFactsJson) {
        if (quickFactsJson == null || quickFactsJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(quickFactsJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse quickFactsJson: {}", e.getMessage());
            return Map.of();
        }
    }
}
