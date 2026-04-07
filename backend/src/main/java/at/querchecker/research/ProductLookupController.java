package at.querchecker.research;

import at.querchecker.deepLearning.entity.DlExtractionTerm;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import at.querchecker.deepLearning.extraction.LlmApiExtractionModel;
import at.querchecker.entity.WhItem;
import at.querchecker.entity.WhListing;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.repository.WhListingRepository;
import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.ProductLookup;
import at.querchecker.research.model.FullSpecsRequest;
import at.querchecker.research.model.FullSpecsResponse;
import at.querchecker.research.model.IcecatFetchResult;
import at.querchecker.research.model.LookupHistoryEntryDto;
import at.querchecker.research.model.LookupRequest;
import at.querchecker.research.model.LookupResponse;
import at.querchecker.research.model.ProductLookupResult;
import at.querchecker.research.repository.ProductLookupRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/listings/{id}")
@RequiredArgsConstructor
@Tag(name = "ProductLookup", description = "Spec-Lookup und Icecat-Vollspezifikation")
public class ProductLookupController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final WhListingRepository listingRepository;
  private final WhItemRepository whItemRepository;
  private final ProductLookupRepository productLookupRepository;
  private final ProductLookupService productLookupService;
  private final LookupHistoryService lookupHistoryService;
  private final IcecatService icecatService;
  private final DlExtractionTermRepository dlExtractionTermRepository;

  @GetMapping("/lookup/history")
  @Operation(summary = "Lookup-Verlauf für ein Inserat laden")
  public List<LookupHistoryEntryDto> getHistory(@PathVariable Long id) {
    return lookupHistoryService.getHistory(id);
  }

  @PostMapping("/lookup")
  @Operation(summary = "Quick-Facts für ein Inserat per Brave+LLM ermitteln")
  public LookupResponse lookup(@PathVariable Long id, @RequestBody LookupRequest req) {
    log.info("=== LOOKUP START: listingId={}, term=\"{}\" ===", id, req.getLookupTerm());

    WhListing listing = listingRepository
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + id)
      );

    if (listing.getWhCategory() == null) {
      log.warn("Listing id={} has no category — returning NO_SOURCES", id);
      return LookupResponse.builder()
        .lookupStatus(LookupStatus.NO_SOURCES)
        .quickFacts(Map.of())
        .build();
    }
    log.info(
      "Listing found: id={}, categoryId={}, categoryName={}",
      listing.getId(),
      listing.getWhCategory().getId(),
      listing.getWhCategory().getName()
    );

    Map<String, String> condensedSpec = resolveCondensedSpec(id);
    ProductLookupResult result = productLookupService.lookup(
      id,
      req.getLookupTerm(),
      listing.getWhCategory(),
      condensedSpec
    );
    log.info(
      "Lookup result: status={}, quickFacts={}, sourceType={}, sourceDomain={}",
      result.getStatus(),
      result.getQuickFactsJson(),
      result.getSourceType(),
      result.getSourceDomain()
    );

    // Persist the resolved lookup term on WhItem so the listing→ProductLookup link is queryable.
    // Only written on COMPLETE — FAILED/ERROR/etc. mean the term produced no usable result.
    if (result.getStatus() == LookupStatus.COMPLETE) {
      whItemRepository.findByWhListingId(id).ifPresent(whItem -> {
        whItem.setLookupTerm(req.getLookupTerm());
        whItem.setUpdatedAt(java.time.LocalDateTime.now());
        whItemRepository.save(whItem);
      });
    }

    ProductLookup saved = productLookupRepository
      .findByLookupTerm(req.getLookupTerm())
      .orElse(null);
    String icecatId =
      result.getIcecatId() != null
        ? result.getIcecatId()
        : (saved != null ? saved.getIcecatId() : null);
    String icecatSpecsJson = saved != null ? saved.getIcecatSpecsJson() : null;

    LookupResponse response = LookupResponse.builder()
      .lookupStatus(result.getStatus())
      .quickFacts(parseQuickFacts(result.getQuickFactsJson()))
      .icecatId(icecatId)
      .icecatSpecsJson(icecatSpecsJson)
      .sourceType(result.getSourceType())
      .sourceDomain(result.getSourceDomain())
      .siteLabel(result.getSiteLabel())
      .sourceUrl(result.getSourceUrl())
      .featureGroupsJson(result.getFeatureGroupsJson())
      .retryAfter(result.getRetryAfter() != null ? result.getRetryAfter().toString() : null)
      .retryAfterSeconds(result.getRetryAfterSeconds())
      .retryProvider(result.getRetryProvider())
      .retryModel(result.getRetryModel())
      .build();

    // Persist to history on every user-initiated search (COMPLETE + FAILED), skip transient states
    boolean shouldSave = result.getStatus() == LookupStatus.COMPLETE
        || result.getStatus() == LookupStatus.FAILED;
    List<LookupHistoryEntryDto> history = shouldSave
        ? lookupHistoryService.saveAndGetHistory(id, req.getLookupTerm(), response)
        : lookupHistoryService.getHistory(id);
    response.setHistory(history);

    log.info(
      "=== LOOKUP END: status={}, quickFactsCount={} ===",
      response.getLookupStatus(),
      response.getQuickFacts().size()
    );
    return response;
  }

  @PostMapping("/lookup/full-specs")
  @Operation(summary = "Vollständige Icecat-Spezifikation laden und cachen")
  public FullSpecsResponse fullSpecs(@PathVariable Long id, @RequestBody FullSpecsRequest req) {
    log.info("=== FULL-SPECS START: listingId={}, icecatId={} ===", id, req.getIcecatId());
    ProductLookup lookup = productLookupRepository
      .findFirstByIcecatIdOrderByUpdatedAtDesc(req.getIcecatId())
      .orElse(null);

    if (lookup == null) {
      // No ProductLookup entry exists for this icecatId — stale ID from frontend store.
      log.warn(
        "=== FULL-SPECS END: No ProductLookup found for icecatId={} — returning empty ===",
        req.getIcecatId()
      );
      return new FullSpecsResponse(null);
    }

    // Return cached specs immediately — no Icecat API call needed.
    if (lookup.getIcecatSpecsJson() != null) {
      log.info("=== FULL-SPECS END: Returning cached specs for icecatId={} ===", req.getIcecatId());
      return new FullSpecsResponse(lookup.getIcecatSpecsJson());
    }

    IcecatFetchResult fetch;
    try {
      fetch = icecatService.fetchFullSpecs(req.getIcecatId());
    } catch (Exception e) {
      log.warn(
        "=== FULL-SPECS END: Icecat fetch failed for icecatId={}: {} ===",
        req.getIcecatId(),
        e.getMessage()
      );
      return new FullSpecsResponse(null);
    }

    if (fetch.isNotFound()) {
      // Icecat reported 404 → the stored icecatId is invalid (e.g. extracted from a file URL).
      // Clear it so this bad ID is never retried.
      log.warn(
        "=== FULL-SPECS END: Icecat 404 for icecatId={} — clearing from ProductLookup ===",
        req.getIcecatId()
      );
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

    log.info(
      "=== FULL-SPECS END: icecatId={}, jsonLength={} ===",
      req.getIcecatId(),
      fetch.json() != null ? fetch.json().length() : 0
    );
    return new FullSpecsResponse(fetch.json());
  }

  // --- private helpers ---

  private Map<String, String> resolveCondensedSpec(Long listingId) {
    try {
      return dlExtractionTermRepository
        .findByListingIdAndModelNameWithCondensedSpec(listingId, LlmApiExtractionModel.MODEL_NAME)
        .stream()
        .findFirst()
        .map(DlExtractionTerm::getCondensedSpecsJson)
        .map((json) -> {
          try {
            return MAPPER.<Map<String, String>>readValue(json, new TypeReference<>() {});
          } catch (Exception e) {
            log.warn(
              "Failed to parse condensedSpecsJson for listingId={}: {}",
              listingId,
              e.getMessage()
            );
            return null;
          }
        })
        .orElse(null);
    } catch (Exception e) {
      log.warn("Could not resolve condensedSpec for listingId={}: {}", listingId, e.getMessage());
      return null;
    }
  }

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
