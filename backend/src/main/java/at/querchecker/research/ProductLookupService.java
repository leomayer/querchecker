package at.querchecker.research;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.extraction.ExtractionProviderRouter;
import at.querchecker.api.search.SearchProperties;
import at.querchecker.api.search.SearchProvider;
import at.querchecker.api.search.WebSearchProviderRouter;
import at.querchecker.api.service.QuotaService;
import at.querchecker.api.service.QuotaStatus;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.deepLearning.service.DlPromptResolver;
import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySearchSource;
import at.querchecker.research.entity.ExtractionQuality;
import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.ProductLookup;
import at.querchecker.research.model.ProductLookupResult;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.model.SearchResult;
import at.querchecker.research.repository.ProductLookupRepository;
import at.querchecker.service.AppConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestriert den vollständigen Spec-Lookup-Ablauf:
 * Cache → Quota → Quellen-Schleife (Brave + LLM) → Persist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductLookupService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProductLookupRepository repo;
    private final QuotaService quotaService;
    private final SearchProperties searchProperties;
    private final WebSearchProviderRouter webSearchRouter;
    private final ExtractionProviderRouter extractionRouter;
    private final DlPromptResolver promptResolver;
    private final CategorySpecPreferenceService prefService;
    private final CategorySearchSourceService sourceService;
    private final ExtractionQualityEvaluator qualityEvaluator;
    private final UrlValidator urlValidator;
    private final HtmlFetchService htmlFetchService;
    private final AppConfigService appConfigService;

    public ProductLookupResult lookup(
        String lookupTerm,
        WhCategory whCategory
    ) {
        log.info("[ProductLookupService] === LOOKUP START ===");
        log.info("[ProductLookupService] Category: id={}, name={}, level={}",
                whCategory.getId(), whCategory.getName(), whCategory.getLevel());

        // 1. Cache-Check
        Optional<ProductLookup> cached = repo.findByLookupTerm(lookupTerm);
        if (cached.isPresent()) {
            LookupStatus status = cached.get().getLookupStatus();
            if (status == LookupStatus.COMPLETE) return fromCache(cached.get());
            if (status == LookupStatus.FAILED) {
                int ttlHours = appConfigService.getLookupFailedTtlHours();
                LocalDateTime updatedAt = cached.get().getUpdatedAt();
                if (updatedAt.isAfter(LocalDateTime.now().minusHours(ttlHours))) {
                    return ProductLookupResult.builder()
                        .status(LookupStatus.FAILED)
                        .retryAfter(updatedAt.plusHours(ttlHours))
                        .build();
                }
                // TTL abgelaufen → neu suchen
            }
            if (status == LookupStatus.ERROR) {
                int ttlMinutes = appConfigService.getLookupErrorTtlMinutes();
                LocalDateTime updatedAt = cached.get().getUpdatedAt();
                if (updatedAt.isAfter(LocalDateTime.now().minusMinutes(ttlMinutes))) {
                    return ProductLookupResult.builder()
                        .status(LookupStatus.ERROR)
                        .retryAfter(updatedAt.plusMinutes(ttlMinutes))
                        .build();
                }
                // TTL abgelaufen → neu suchen
            }
            // QUOTA_EXCEEDED → weiter zu Kontingent-Check
        }

        // 2. Kontingent-Check
        Provider searchProvider = searchProperties.getActiveProvider() == SearchProvider.GOOGLE_DISCOVERY
                ? Provider.GOOGLE_DISCOVERY
                : Provider.BRAVE;
        if (
            quotaService.checkQuota(searchProvider) ==
            QuotaStatus.QUOTA_EXCEEDED
        ) {
            save(lookupTerm, LookupStatus.QUOTA_EXCEEDED, null);
            return ProductLookupResult.quotaExceeded();
        }

        // 3. Quellen laden
        List<CategorySearchSource> sources = sourceService.findForCategory(
            whCategory
        );
        log.info("[ProductLookupService] Found {} sources for category {}", sources.size(), whCategory.getName());
        for (CategorySearchSource src : sources) {
            log.info("[ProductLookupService]   - Source: type={}, domain={}, inherit={}",
                    src.getSourceType(), src.getSiteDomain(), src.isInheritFromParent());
        }

        if (sources.isEmpty()) {
            log.warn("[ProductLookupService] NO SOURCES FOUND for category id={} name={}",
                    whCategory.getId(), whCategory.getName());
            // NO_SOURCES wird nicht gecacht — beim nächsten Aufruf erneut geprüft
            return ProductLookupResult.noSources();
        }

        // 4. Felder laden (einmalig — unabhängig von der Quellen-Schleife)
        List<String> mandatory = prefService.getMandatoryFields(whCategory);
        List<String> queryKeywords = prefService.getQueryKeywords(whCategory);

        // 5. Quellen-Schleife
        PartialResult bestPartial = null;

        try {
        for (CategorySearchSource source : sources) {
            log.debug("[ProductLookupService] Web search: type={}, domain={}, term='{}'",
                source.getSourceType(), source.getSiteDomain(), lookupTerm);
            List<SearchResult> braveResults = webSearchRouter.getActive().search(
                lookupTerm,
                source.getSiteDomain(),
                queryKeywords,
                source.getQueryExcludes(),
                source.getSearchResultCount()
            );
            log.debug("[ProductLookupService] Web search returned {} results for '{}' via {}",
                braveResults.size(), lookupTerm, source.getSiteDomain());

            if (braveResults.isEmpty()) continue;

            // Extraktion: HTML-Fetch oder Snippets
            QuickFactsResult extracted;
            String resolvedFetchUrl = null;

            if (htmlFetchService.shouldFetchFullPage(source.getSourceType())) {
                // HTML-Fetch-Pfad: Fallback-Loop über alle Brave-Treffer
                Optional<String> html = Optional.empty();
                for (SearchResult candidate : braveResults) {
                    if (
                        !urlValidator.matchesExpectedPattern(
                            candidate.getUrl(),
                            source.getSourceType()
                        )
                    ) {
                        log.debug(
                            "URL-Pattern nicht erwartet, übersprungen: {}",
                            candidate.getUrl()
                        );
                        continue;
                    }
                    html = htmlFetchService.fetchAndExtract(
                        candidate.getUrl(),
                        source.getSourceType()
                    );
                    if (html.isPresent()) {
                        resolvedFetchUrl = candidate.getUrl();
                        log.debug(
                            "HTML-Fetch erfolgreich: {}",
                            resolvedFetchUrl
                        );
                        break;
                    }
                    log.debug(
                        "HTML-Fetch leer/fehlgeschlagen: {}",
                        candidate.getUrl()
                    );
                }

                if (html.isEmpty()) {
                    log.info(
                        "Alle {} Brave-Treffer für '{}' via {} fehlgeschlagen",
                        braveResults.size(),
                        lookupTerm,
                        source.getSiteDomain()
                    );
                    continue;
                }

                extracted = extractionRouter
                    .getActive()
                    .extractQuickFactsFromText(
                        lookupTerm,
                        whCategory != null ? whCategory.getName() : "",
                        html.get(),
                        mandatory,
                        promptResolver.resolve(
                            whCategory,
                            PromptType.HTML_FULL_SPECS
                        )
                    );
            } else {
                // Snippets-Pfad: alle braveResults ans LLM
                extracted = extractionRouter
                    .getActive()
                    .extractQuickFacts(
                        lookupTerm,
                        whCategory != null ? whCategory.getName() : "",
                        braveResults,
                        mandatory,
                        promptResolver.resolve(
                            whCategory,
                            PromptType.QUICK_FACTS
                        )
                    );
            }

            // URL-Validierung
            String icecatId = urlValidator.resolveIcecatId(
                extracted.getSources() != null
                    ? extracted.getSources().getIcecatId()
                    : null,
                braveResults
            );

            // HTML-Fetch: sourceUrl von Java (resolvedFetchUrl), nicht vom LLM
            String sourceUrl =
                resolvedFetchUrl != null
                    ? resolvedFetchUrl
                    : urlValidator.resolveSourceUrl(
                          extracted.getSources() != null
                              ? extracted.getSources().getSourceUrl()
                              : null,
                          braveResults
                      );

            // Pattern-Check nur beim Snippets-Pfad nötig (HTML-Fetch hat bereits gecheckt)
            if (
                resolvedFetchUrl == null &&
                !urlValidator.matchesExpectedPattern(
                    sourceUrl,
                    source.getSourceType()
                )
            ) {
                sourceUrl = null;
            }

            // Qualitätsprüfung
            ExtractionQuality quality = qualityEvaluator.evaluate(
                extracted,
                mandatory,
                source.getSourceType()
            );

            switch (quality) {
                case GOOD -> {
                    return saveAndReturn(
                        lookupTerm,
                        extracted,
                        icecatId,
                        sourceUrl,
                        source,
                        LookupStatus.COMPLETE
                    );
                }
                case PARTIAL -> {
                    if (bestPartial == null) {
                        bestPartial = new PartialResult(
                            extracted,
                            icecatId,
                            sourceUrl,
                            source
                        );
                    }
                }
                case FAILED_NO_CRITERIA -> {
                    save(lookupTerm, LookupStatus.FAILED, null);
                    return ProductLookupResult.failed();
                }
                // EMPTY → nächste Quelle
            }
        }

        // 6. Kein GOOD → bestes PARTIAL verwenden
        if (bestPartial != null) {
            return saveAndReturn(
                lookupTerm,
                bestPartial.result(),
                bestPartial.icecatId(),
                bestPartial.sourceUrl(),
                bestPartial.source(),
                LookupStatus.COMPLETE
            );
        }

        save(lookupTerm, LookupStatus.FAILED, null);
        return ProductLookupResult.failed();

        } catch (Exception e) {
            log.error("[ProductLookupService] Unerwarteter Fehler bei Lookup für '{}': {}", lookupTerm, e.getMessage(), e);
            save(lookupTerm, LookupStatus.ERROR, null);
            return ProductLookupResult.error();
        }
    }

    // --- private helpers ---

    private ProductLookupResult saveAndReturn(
        String lookupTerm,
        QuickFactsResult extracted,
        String icecatId,
        String sourceUrl,
        CategorySearchSource source,
        LookupStatus status
    ) {
        ProductLookup pl = repo
            .findByLookupTerm(lookupTerm)
            .orElseGet(ProductLookup::new);

        pl.setLookupTerm(lookupTerm);
        pl.setLookupStatus(status);
        pl.setQuickFactsJson(toJson(extracted.getQuickFacts()));
        pl.setQuickFactsFetchedAt(LocalDateTime.now());
        pl.setIcecatId(icecatId);
        pl.setSourceType(source.getSourceType());
        pl.setSourceDomain(source.getSiteDomain());
        pl.setSourceUrl(sourceUrl);

        if (
            extracted.getFeatureGroups() != null &&
            !extracted.getFeatureGroups().isEmpty()
        ) {
            pl.setFeatureGroupsJson(toJson(extracted.getFeatureGroups()));
        }

        repo.save(pl);

        return ProductLookupResult.builder()
            .status(status)
            .quickFactsJson(pl.getQuickFactsJson())
            .featureGroupsJson(pl.getFeatureGroupsJson())
            .icecatId(icecatId)
            .sourceType(source.getSourceType())
            .sourceDomain(source.getSiteDomain())
            .siteLabel(source.getSiteLabel())
            .sourceUrl(sourceUrl)
            .build();
    }

    private void save(
        String lookupTerm,
        LookupStatus status,
        String quickFactsJson
    ) {
        ProductLookup pl = repo
            .findByLookupTerm(lookupTerm)
            .orElseGet(ProductLookup::new);
        pl.setLookupTerm(lookupTerm);
        pl.setLookupStatus(status);
        pl.setQuickFactsJson(quickFactsJson);
        repo.save(pl);
    }

    private ProductLookupResult fromCache(ProductLookup pl) {
        return ProductLookupResult.builder()
            .status(pl.getLookupStatus())
            .quickFactsJson(pl.getQuickFactsJson())
            .featureGroupsJson(pl.getFeatureGroupsJson())
            .icecatId(pl.getIcecatId())
            .sourceType(pl.getSourceType())
            .sourceDomain(pl.getSourceDomain())
            .sourceUrl(pl.getSourceUrl())
            .build();
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private record PartialResult(
        QuickFactsResult result,
        String icecatId,
        String sourceUrl,
        CategorySearchSource source
    ) {}
}
