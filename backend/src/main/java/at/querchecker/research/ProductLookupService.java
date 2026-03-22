package at.querchecker.research;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.service.QuotaService;
import at.querchecker.api.service.QuotaStatus;
import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.ProductLookup;
import at.querchecker.research.model.BraveResult;
import at.querchecker.research.model.ProductLookupResult;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.repository.ProductLookupRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Orchestriert den vollständigen Spec-Lookup-Ablauf:
 * Cache → Quota → Brave Search → LLM-Extraktion → Persist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductLookupService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProductLookupRepository repo;
    private final QuotaService quotaService;
    private final BraveSearchService braveSearchService;
    private final GroqExtractionService groqExtractionService;
    private final CategorySpecPreferenceService prefService;

    public ProductLookupResult lookup(String lookupTerm, WhCategory whCategory) {

        // 1. Cache-Check
        Optional<ProductLookup> cached = repo.findByLookupTerm(lookupTerm);
        if (cached.isPresent()) {
            LookupStatus status = cached.get().getLookupStatus();
            if (status == LookupStatus.COMPLETE) {
                return new ProductLookupResult(LookupStatus.COMPLETE, cached.get().getQuickFactsJson());
            }
            if (status == LookupStatus.FAILED) {
                return ProductLookupResult.failed();
            }
            // QUOTA_EXCEEDED → weiter zu Kontingent-Check (neue Periode?)
        }

        // 2. Kontingent-Check
        if (quotaService.checkQuota(Provider.BRAVE) == QuotaStatus.QUOTA_EXCEEDED) {
            saveLookup(lookupTerm, LookupStatus.QUOTA_EXCEEDED, null, null, null, null);
            return ProductLookupResult.quotaExceeded();
        }

        // 3. Präferenzen holen
        List<String> keywords = prefService.getQueryKeywords(whCategory);
        List<String> mandatoryFields = prefService.getMandatoryFields(whCategory);

        // 4. Brave Search
        List<BraveResult> results = braveSearchService.search(lookupTerm, keywords);
        if (results.isEmpty()) {
            saveLookup(lookupTerm, LookupStatus.FAILED, null, null, null, null);
            return ProductLookupResult.failed();
        }

        // 5. LLM-Extraktion
        QuickFactsResult qfr = groqExtractionService.extractFromSnippets(lookupTerm, whCategory, results, mandatoryFields);
        String icecatId = qfr.getSources() != null ? qfr.getSources().getIcecatId() : null;
        String icecatUrl = qfr.getSources() != null ? qfr.getSources().getIcecatUrl() : null;
        log.debug("QuickFacts for '{}': facts={}, icecatId={}, icecatUrl={}",
            lookupTerm, qfr.getQuickFacts(), icecatId, icecatUrl);

        // 6. COMPLETE speichern
        String quickFactsJson = serializeQuickFacts(qfr);
        List<String> sourceUrls = results.stream().map(BraveResult::getUrl).toList();

        saveLookup(lookupTerm, LookupStatus.COMPLETE, quickFactsJson, icecatId, icecatUrl, sourceUrls);
        return new ProductLookupResult(LookupStatus.COMPLETE, quickFactsJson);
    }

    // --- private helpers ---

    private void saveLookup(String lookupTerm, LookupStatus status,
                             String quickFactsJson, String icecatId, String icecatUrl,
                             List<String> sourceUrls) {
        ProductLookup.ProductLookupBuilder builder = ProductLookup.builder()
                .lookupTerm(lookupTerm)
                .lookupStatus(status)
                .quickFactsJson(quickFactsJson)
                .icecatId(icecatId)
                .icecatUrl(icecatUrl)
                .sourceUrls(sourceUrls);

        if (status == LookupStatus.COMPLETE) {
            builder.quickFactsFetchedAt(LocalDateTime.now());
        }
        repo.save(builder.build());
    }

    private String serializeQuickFacts(QuickFactsResult qfr) {
        try {
            return MAPPER.writeValueAsString(qfr.getQuickFacts());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize quickFacts: {}", e.getMessage());
            return "{}";
        }
    }
}
