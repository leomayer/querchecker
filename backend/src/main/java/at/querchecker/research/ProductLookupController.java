package at.querchecker.research;

import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.research.entity.ProductLookup;
import at.querchecker.research.model.FullSpecsRequest;
import at.querchecker.research.model.FullSpecsResponse;
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
        WhListing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + id));

        ProductLookupResult result = productLookupService.lookup(req.getLookupTerm(), listing.getWhCategory());

        String icecatId = productLookupRepository.findByLookupTerm(req.getLookupTerm())
                .map(ProductLookup::getIcecatId)
                .orElse(null);

        return new LookupResponse(result.getStatus(), parseQuickFacts(result.getQuickFactsJson()), icecatId);
    }

    @PostMapping("/lookup/full-specs")
    @Operation(summary = "Vollständige Icecat-Spezifikation laden und cachen")
    public FullSpecsResponse fullSpecs(@PathVariable Long id, @RequestBody FullSpecsRequest req) {
        ProductLookup lookup = productLookupRepository.findByIcecatId(req.getIcecatId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Kein ProductLookup für icecatId: " + req.getIcecatId()));

        String specs = icecatService.fetchFullSpecs(req.getIcecatId());
        if (specs != null) {
            lookup.setIcecatSpecsJson(specs);
            lookup.setIcecatFetchedAt(LocalDateTime.now());
            productLookupRepository.save(lookup);
        }

        return new FullSpecsResponse(specs);
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
