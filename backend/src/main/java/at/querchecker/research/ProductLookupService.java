package at.querchecker.research;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.exception.RateLimitException;
import at.querchecker.api.extraction.ExtractionProviderRouter;
import at.querchecker.api.search.SearchProperties;
import at.querchecker.api.search.SearchProvider;
import at.querchecker.api.search.WebSearchProviderRouter;
import at.querchecker.api.service.QuotaService;
import at.querchecker.api.service.QuotaStatus;
import at.querchecker.deepLearning.entity.PromptType;
import at.querchecker.deepLearning.service.DlPromptResolver;
import at.querchecker.entity.WhCategory;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.research.entity.CategorySearchSource;
import at.querchecker.research.entity.ExtractionQuality;
import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.ProductLookup;
import at.querchecker.research.model.LookupResultPayload;
import at.querchecker.research.model.ProductLookupResult;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.model.SearchResult;
import at.querchecker.research.repository.ProductLookupRepository;
import at.querchecker.service.AppConfigService;
import at.querchecker.sse.ErrorNotificationPayload;
import at.querchecker.sse.SseEvent;
import at.querchecker.sse.SseHub;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
    private static final int MAX_AUTO_RETRY_SECONDS = 61; // per-minute window max (60s) + 1s buffer

    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> pendingRetries = new ConcurrentHashMap<>();

    @PreDestroy
    void shutdown() {
        retryScheduler.shutdownNow();
    }

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
    private final AppConfigService appConfigService;
    private final SearchResultCacheService searchResultCacheService;
    private final SseHub sseHub;
    private final WhItemRepository whItemRepository;

    /**
     * @param listingId required for SSE push on rate-limit retry; pass null to suppress retry scheduling
     */
    public ProductLookupResult lookup(
        Long listingId,
        String lookupTerm,
        WhCategory whCategory,
        Map<String, String> condensedSpec
    ) {
        log.info("[ProductLookupService] === LOOKUP START ===");
        log.info("[ProductLookupService] Category: id={}, name={}, level={}",
                whCategory.getId(), whCategory.getName(), whCategory.getLevel());

        // A new lookup call means the user moved to a new item — all pending retries are stale
        if (listingId != null) {
            cancelAllPendingRetries();
        }

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
            return ProductLookupResult.noSources();
        }

        // 4. Felder laden (einmalig — unabhängig von der Quellen-Schleife)
        List<String> mandatory = prefService.getMandatoryFields(whCategory);
        List<String> queryKeywords = prefService.getQueryKeywords(whCategory);
        String condensedSpecContext = formatCondensedSpec(condensedSpec);

        // 5. Quellen-Schleife (Snippets-Pfad für alle Quellen)
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

            // Cache results before LLM call — available for rate-limit retry
            searchResultCacheService.put(lookupTerm, source.getSiteDomain(), braveResults);

            QuickFactsResult extracted = extractionRouter
                .getActive()
                .extractQuickFacts(
                    lookupTerm,
                    whCategory != null ? whCategory.getName() : "",
                    braveResults,
                    mandatory,
                    promptResolver.resolve(whCategory, PromptType.QUICK_FACTS),
                    condensedSpecContext
                );

            String icecatId = urlValidator.resolveIcecatId(
                extracted.getSources() != null ? extracted.getSources().getIcecatId() : null,
                braveResults
            );

            String sourceUrl = urlValidator.resolveSourceUrl(
                extracted.getSources() != null ? extracted.getSources().getSourceUrl() : null,
                braveResults
            );
            sourceUrl = urlValidator.normalizeUrl(sourceUrl);

            if (!urlValidator.matchesExpectedPattern(sourceUrl, source.getSourceType())) {
                sourceUrl = null;
            }

            ExtractionQuality quality = qualityEvaluator.evaluate(extracted, mandatory, source.getSourceType());

            switch (quality) {
                case GOOD -> {
                    return saveAndReturn(lookupTerm, extracted, icecatId, sourceUrl, source, LookupStatus.COMPLETE);
                }
                case PARTIAL -> {
                    if (bestPartial == null) {
                        bestPartial = new PartialResult(extracted, icecatId, sourceUrl, source);
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

        } catch (RateLimitException rle) {
            log.warn("[ProductLookupService] Rate limited by {} — retryAfter={}s, listingId={}",
                    rle.getProvider(), rle.getRetryAfterSeconds(), listingId);
            if (listingId != null && rle.getRetryAfterSeconds() <= MAX_AUTO_RETRY_SECONDS) {
                scheduleRetry(listingId, lookupTerm, whCategory, condensedSpec,
                        mandatory, queryKeywords, condensedSpecContext, sources, rle.getRetryAfterSeconds());
            } else {
                // Retry cap exceeded or no listingId — send error notification
                broadcastErrorNotification(listingId, "RATE_LIMITED",
                    "LLM-Kontingent erreicht — bitte später erneut versuchen", (long) rle.getRetryAfterSeconds());
            }
            return ProductLookupResult.rateLimited(rle.getRetryAfterSeconds(),
                    rle.getProvider().getDisplayName(), rle.getModelName());
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";

            // Detect rate limit errors hidden in HTTP responses (e.g., 413 Payload Too Large with rate_limit_exceeded)
            if (errorMsg.contains("rate_limit_exceeded")) {
                log.warn("[ProductLookupService] Rate limit detected in error response: {}", errorMsg);
                save(lookupTerm, LookupStatus.ERROR, null);
                broadcastErrorNotification(listingId, "RATE_LIMITED",
                    "LLM-Kontingent erreicht — bitte später erneut versuchen", 60L); // Default 60s retry
                return ProductLookupResult.rateLimited(60, "Provider", "Unknown");
            }

            log.error("[ProductLookupService] Unerwarteter Fehler bei Lookup für '{}': {}", lookupTerm, errorMsg, e);
            save(lookupTerm, LookupStatus.ERROR, null);
            String errorType = errorMsg.contains("timeout") ? "API_TIMEOUT" : "LOOKUP_FAILED";
            String message = errorType.equals("API_TIMEOUT")
                ? "Anfrage-Timeout — Server antwortet nicht rechtzeitig"
                : "Produktsuche fehlgeschlagen — bitte später erneut versuchen";
            broadcastErrorNotification(listingId, errorType, message, null);
            return ProductLookupResult.error();
        }
    }

    // --- private helpers ---

    private void cancelAllPendingRetries() {
        pendingRetries.forEach((id, future) -> {
            if (!future.isDone()) {
                future.cancel(false);
                log.debug("[ProductLookupService] Cancelled stale retry for listingId={}", id);
            }
        });
        pendingRetries.clear();
    }

    private void scheduleRetry(
            Long listingId, String lookupTerm, WhCategory whCategory,
            Map<String, String> condensedSpec, List<String> mandatory, List<String> queryKeywords,
            String condensedSpecContext, List<CategorySearchSource> sources, int delaySeconds) {

        ScheduledFuture<?> future = retryScheduler.schedule(() -> {
            pendingRetries.remove(listingId);
            log.info("[ProductLookupService] Retrying rate-limited lookup: listingId={}, term='{}' after {}s",
                    listingId, lookupTerm, delaySeconds);
            try {
                // Short-circuit if the term was already completed (e.g. by another listing's retry)
                Optional<ProductLookup> cached = repo.findByLookupTerm(lookupTerm);
                if (cached.isPresent() && cached.get().getLookupStatus() == LookupStatus.COMPLETE) {
                    log.debug("[ProductLookupService] Retry short-circuit: term already COMPLETE in cache");
                    sseHub.broadcast("lookup-result", buildSsePayload(listingId, fromCache(cached.get()), null, null));
                    return;
                }

                PartialResult bestPartial = null;

                for (CategorySearchSource source : sources) {
                    // Use cached results first; fall back to web search
                    List<SearchResult> results = searchResultCacheService.get(lookupTerm, source.getSiteDomain());
                    if (results == null) {
                        results = webSearchRouter.getActive().search(
                                lookupTerm, source.getSiteDomain(), queryKeywords,
                                source.getQueryExcludes(), source.getSearchResultCount());
                        if (!results.isEmpty()) {
                            searchResultCacheService.put(lookupTerm, source.getSiteDomain(), results);
                        }
                    }
                    if (results.isEmpty()) continue;

                    QuickFactsResult extracted = extractionRouter.getActive().extractQuickFacts(
                            lookupTerm, whCategory.getName(), results, mandatory,
                            promptResolver.resolve(whCategory, PromptType.QUICK_FACTS), condensedSpecContext);

                    String icecatId = urlValidator.resolveIcecatId(
                            extracted.getSources() != null ? extracted.getSources().getIcecatId() : null, results);
                    String sourceUrl = urlValidator.resolveSourceUrl(
                            extracted.getSources() != null ? extracted.getSources().getSourceUrl() : null, results);
                    sourceUrl = urlValidator.normalizeUrl(sourceUrl);
                    if (!urlValidator.matchesExpectedPattern(sourceUrl, source.getSourceType())) sourceUrl = null;

                    ExtractionQuality quality = qualityEvaluator.evaluate(extracted, mandatory, source.getSourceType());

                    if (quality == ExtractionQuality.GOOD) {
                        ProductLookupResult result = saveAndReturn(lookupTerm, extracted, icecatId, sourceUrl, source, LookupStatus.COMPLETE);
                        sseHub.broadcast("lookup-result", buildSsePayload(listingId, result, null, null));
                        return;
                    } else if (quality == ExtractionQuality.PARTIAL && bestPartial == null) {
                        bestPartial = new PartialResult(extracted, icecatId, sourceUrl, source);
                    } else if (quality == ExtractionQuality.FAILED_NO_CRITERIA) {
                        save(lookupTerm, LookupStatus.FAILED, null);
                        sseHub.broadcast("lookup-result", buildSsePayload(listingId, ProductLookupResult.failed(), null, null));
                        return;
                    }
                }

                if (bestPartial != null) {
                    ProductLookupResult result = saveAndReturn(lookupTerm, bestPartial.result(),
                            bestPartial.icecatId(), bestPartial.sourceUrl(), bestPartial.source(), LookupStatus.COMPLETE);
                    sseHub.broadcast("lookup-result", buildSsePayload(listingId, result, null, null));
                    return;
                }

                save(lookupTerm, LookupStatus.FAILED, null);
                sseHub.broadcast("lookup-result", buildSsePayload(listingId, ProductLookupResult.failed(), null, null));

            } catch (RateLimitException rle) {
                log.warn("[ProductLookupService] Rate-limit retry hit a second limit for listingId={}: retryAfter={}s",
                        listingId, rle.getRetryAfterSeconds());
                if (rle.getRetryAfterSeconds() <= MAX_AUTO_RETRY_SECONDS) {
                    scheduleRetry(listingId, lookupTerm, whCategory, condensedSpec,
                            mandatory, queryKeywords, condensedSpecContext, sources, rle.getRetryAfterSeconds());
                } else {
                    log.warn("[ProductLookupService] Giving up — retryAfter={}s exceeds cap of {}s",
                            rle.getRetryAfterSeconds(), MAX_AUTO_RETRY_SECONDS);
                    save(lookupTerm, LookupStatus.ERROR, null);
                    sseHub.broadcast("lookup-result", buildSsePayload(listingId, ProductLookupResult.error(), null, null));
                }
            } catch (Exception e) {
                log.warn("[ProductLookupService] Rate-limit retry failed for listingId={}: {}", listingId, e.getMessage());
                save(lookupTerm, LookupStatus.ERROR, null);
                sseHub.broadcast("lookup-result", buildSsePayload(listingId, ProductLookupResult.error(), null, null));
            }
        }, delaySeconds, TimeUnit.SECONDS);

        pendingRetries.put(listingId, future);
    }

    private LookupResultPayload buildSsePayload(Long listingId, ProductLookupResult result,
                                                 String retryProvider, String retryModel) {
        return new LookupResultPayload(
                listingId,
                result.getStatus(),
                parseQuickFacts(result.getQuickFactsJson()),
                result.getIcecatId(),
                result.getSourceType(),
                result.getSourceDomain(),
                result.getSiteLabel(),
                result.getSourceUrl(),
                result.getFeatureGroupsJson(),
                retryProvider,
                retryModel
        );
    }

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

    private Map<String, String> parseQuickFacts(String quickFactsJson) {
        if (quickFactsJson == null || quickFactsJson.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(quickFactsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse quickFactsJson: {}", e.getMessage());
            return Map.of();
        }
    }

    private String formatCondensedSpec(Map<String, String> condensedSpec) {
        if (condensedSpec == null || condensedSpec.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nKontext aus Inserat (Variantenauswahl):\n");
        condensedSpec.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }

    private void broadcastErrorNotification(Long listingId, String errorType, String message, Long retryAfterSeconds) {
        if (listingId == null) return;
        try {
            // Map listingId to whItemId
            Long whItemId = whItemRepository.findByWhListingId(listingId)
                    .map(item -> item.getId())
                    .orElse(null);
            if (whItemId == null) {
                log.debug("No WhItem found for listingId={}, skipping error notification broadcast", listingId);
                return;
            }
            ErrorNotificationPayload payload = new ErrorNotificationPayload(errorType, message, retryAfterSeconds);
            SseEvent<ErrorNotificationPayload> event = new SseEvent<>("error-notification", whItemId, payload);
            sseHub.broadcast("error-notification", event);
        } catch (Exception e) {
            log.warn("Failed to broadcast error notification for listingId={}", listingId, e);
        }
    }

    private record PartialResult(
        QuickFactsResult result,
        String icecatId,
        String sourceUrl,
        CategorySearchSource source
    ) {}
}
