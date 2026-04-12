package at.querchecker.api.search;

import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.exception.RateLimitException;
import at.querchecker.api.result.ApiCallResult;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.config.ProviderStatusService;
import at.querchecker.research.model.BraveApiResponse;
import at.querchecker.research.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Brave Search-Implementierung von WebSearchService.
 * Dreistufige Fallback-Strategie: mit Keywords → technische Daten → direkte Suche.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BraveWebSearchService implements WebSearchService {

    private static final String BRAVE_API_URL = "https://api.search.brave.com/res/v1/web/search";

    private final RestTemplate restTemplate;
    private final ApiUsageLogService usageLogService;
    private final ProviderProperties providerProperties;
    private final ProviderStatusService providerStatusService;

    @Override
    public SearchProvider getProvider() {
        return SearchProvider.BRAVE;
    }

    @Override
    public ApiCallResult<List<SearchResult>> search(String lookupTerm, String siteDomain,
                                                    List<String> keywords, List<String> queryExcludes,
                                                    int resultCount) {
        log.trace("[BraveSearch] START term='{}' site={} keywords={} excludes={} count={}",
                lookupTerm, siteDomain, keywords, queryExcludes, resultCount);

        // Stufe 1: mit Präferenz-Keywords + Spezifikationen
        String q1 = buildStage1Query(lookupTerm, siteDomain, keywords, queryExcludes);
        log.trace("[BraveSearch] Stufe 1: query='{}'", q1);
        ApiCallResult<List<SearchResult>> r1 = callBrave(q1, lookupTerm, resultCount);
        if (!(r1 instanceof ApiCallResult.Success<List<SearchResult>> s1)) return r1;
        if (!s1.value().isEmpty()) { log.trace("[BraveSearch] Stufe 1: {} Ergebnisse", s1.value().size()); return r1; }

        // Stufe 2: Spezifikationen technische Daten
        String q2 = buildStage2Query(lookupTerm, siteDomain, queryExcludes);
        log.trace("[BraveSearch] Stufe 2 (Fallback): query='{}'", q2);
        ApiCallResult<List<SearchResult>> r2 = callBrave(q2, lookupTerm, resultCount);
        if (!(r2 instanceof ApiCallResult.Success<List<SearchResult>> s2)) return r2;
        if (!s2.value().isEmpty()) { log.trace("[BraveSearch] Stufe 2: {} Ergebnisse", s2.value().size()); return r2; }

        // Stufe 3: direkte Suche (letzter Fallback, keine Negativ-Filter)
        String q3 = buildStage3Query(lookupTerm, siteDomain);
        log.trace("[BraveSearch] Stufe 3 (letzter Fallback): query='{}'", q3);
        ApiCallResult<List<SearchResult>> r3 = callBrave(q3, lookupTerm, resultCount);
        if (r3 instanceof ApiCallResult.Success<List<SearchResult>> s3) {
            log.trace("[BraveSearch] Stufe 3: {} Ergebnisse", s3.value().size());
        }
        return r3;
    }

    private ApiCallResult<List<SearchResult>> callBrave(String query, String lookupTerm, int count) {
        ProviderConfig config = providerProperties.getProvider(Provider.BRAVE);
        String url = buildUrl(query, count);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("X-Subscription-Token", config.getApiKey());

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<BraveApiResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), BraveApiResponse.class);
            long duration = System.currentTimeMillis() - start;

            usageLogService.log(Provider.BRAVE, RequestType.SEARCH, lookupTerm,
                    response.getStatusCode().value(), null, null, duration, null);
            List<SearchResult> extracted = extractResults(response.getBody());
            providerStatusService.markValid(true);
            log.trace("[BraveSearch] HTTP {} durationMs={}", response.getStatusCode().value(), duration);
            if (log.isTraceEnabled()) {
                extracted.forEach(r -> log.trace("[BraveSearch]   URL: {} | desc-len={} | snippets={}",
                        r.getUrl(),
                        r.getDescription() != null ? r.getDescription().length() : 0,
                        r.getExtraSnippets() != null ? r.getExtraSnippets().size() : 0));
            }
            return new ApiCallResult.Success<>(extracted);
        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - start;
            int status = e.getStatusCode().value();
            usageLogService.log(Provider.BRAVE, RequestType.SEARCH, lookupTerm, status, null, null, duration, null);
            if (status == 429) {
                var responseHeaders = e.getResponseHeaders();
                String retryAfterHeader = responseHeaders != null ? responseHeaders.getFirst("Retry-After") : null;
                int retryAfterSeconds = RateLimitException.parseRetryAfter(retryAfterHeader);
                log.warn("[BraveSearch] Rate limited — retryAfter={}s", retryAfterSeconds);
                return new ApiCallResult.RateLimited<>(retryAfterSeconds);
            }
            String reason = e.getMessage();
            if (status == 401 || status == 403) {
                log.warn("[BraveSearch] Unavailable (status={}): {}", status, reason);
                providerStatusService.markUnavailable(true, reason, status);
                return new ApiCallResult.Unavailable<>(reason, status);
            }
            log.warn("[BraveSearch] Unreachable (status={}): {}", status, reason);
            providerStatusService.markUnreachable(true, reason, status);
            return new ApiCallResult.Unreachable<>(reason, status);
        } catch (ResourceAccessException e) {
            long duration = System.currentTimeMillis() - start;
            String reason = "Timeout/Netzwerkfehler: " + e.getMessage();
            log.warn("[BraveSearch] Unreachable (timeout): {}", reason);
            usageLogService.log(Provider.BRAVE, RequestType.SEARCH, lookupTerm, 503, null, null, duration, null);
            providerStatusService.markUnreachable(true, reason, 503);
            return new ApiCallResult.Unreachable<>(reason, 503);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String reason = e.getMessage();
            log.warn("[BraveSearch] Unexpected error for query='{}': {}", query, reason);
            usageLogService.log(Provider.BRAVE, RequestType.SEARCH, lookupTerm, 500, null, null, duration, null);
            return new ApiCallResult.Success<>(List.of()); // unbekannter Fehler → leere Ergebnisse
        }
    }

    private String buildUrl(String query, int count) {
        return UriComponentsBuilder.fromUriString(BRAVE_API_URL)
                .queryParam("q", query)
                .queryParam("count", count)
                .queryParam("extra_snippets", true)
                .build()
                .toUriString();
    }

    private String buildStage1Query(String lookupTerm, String siteDomain,
                                    List<String> keywords, List<String> queryExcludes) {
        StringBuilder q = new StringBuilder(lookupTerm);
        q.append(" Spezifikationen");
        if (keywords != null) {
            keywords.stream().limit(5).forEach(k -> q.append(" ").append(k));
        }
        q.append(" site:").append(siteDomain);
        appendExcludes(q, queryExcludes);
        return q.toString();
    }

    private String buildStage2Query(String lookupTerm, String siteDomain,
                                    List<String> queryExcludes) {
        StringBuilder q = new StringBuilder(lookupTerm);
        q.append(" Spezifikationen technische Daten site:").append(siteDomain);
        appendExcludes(q, queryExcludes);
        return q.toString();
    }

    private String buildStage3Query(String lookupTerm, String siteDomain) {
        return lookupTerm + " site:" + siteDomain;
    }

    private void appendExcludes(StringBuilder q, List<String> queryExcludes) {
        if (queryExcludes != null) {
            queryExcludes.forEach(ex -> q.append(" ").append(ex));
        }
    }

    private List<SearchResult> extractResults(BraveApiResponse body) {
        if (body == null || body.getWeb() == null) return List.of();
        return body.getWeb().getResults().stream()
                .limit(5) // Limit to top 5 results to reduce payload size
                .map(r -> SearchResult.builder()
                        .title(r.getTitle())
                        .url(r.getUrl())
                        .description(truncateString(r.getDescription(), 250)) // Truncate to 250 chars
                        .extraSnippets(truncateSnippets(r.getExtraSnippets(), 7, 250)) // Max 7 snippets, 250 chars each
                        .build())
                .toList();
    }

    private String truncateString(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private List<String> truncateSnippets(List<String> snippets, int maxSnippets, int maxCharsPerSnippet) {
        if (snippets == null || snippets.isEmpty()) return List.of();
        return snippets.stream()
                .limit(maxSnippets)
                .map(s -> truncateString(s, maxCharsPerSnippet))
                .toList();
    }
}
