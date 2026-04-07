package at.querchecker.api.search;

import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.exception.RateLimitException;
import at.querchecker.api.service.ApiUsageLogService;
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

    @Override
    public SearchProvider getProvider() {
        return SearchProvider.BRAVE;
    }

    @Override
    public List<SearchResult> search(String lookupTerm, String siteDomain,
                                     List<String> keywords, List<String> queryExcludes,
                                     int resultCount) {
        log.trace("[BraveSearch] START term='{}' site={} keywords={} excludes={} count={}",
                lookupTerm, siteDomain, keywords, queryExcludes, resultCount);

        // Stufe 1: mit Präferenz-Keywords + Spezifikationen
        String q1 = buildStage1Query(lookupTerm, siteDomain, keywords, queryExcludes);
        log.trace("[BraveSearch] Stufe 1: query='{}'", q1);
        List<SearchResult> results = callBrave(q1, lookupTerm, resultCount);
        log.trace("[BraveSearch] Stufe 1: {} Ergebnisse", results.size());
        if (!results.isEmpty()) return results;

        // Stufe 2: Spezifikationen technische Daten
        String q2 = buildStage2Query(lookupTerm, siteDomain, queryExcludes);
        log.trace("[BraveSearch] Stufe 2 (Fallback): query='{}'", q2);
        results = callBrave(q2, lookupTerm, resultCount);
        log.trace("[BraveSearch] Stufe 2: {} Ergebnisse", results.size());
        if (!results.isEmpty()) return results;

        // Stufe 3: direkte Suche (letzter Fallback, keine Negativ-Filter)
        String q3 = buildStage3Query(lookupTerm, siteDomain);
        log.trace("[BraveSearch] Stufe 3 (letzter Fallback): query='{}'", q3);
        results = callBrave(q3, lookupTerm, resultCount);
        log.trace("[BraveSearch] Stufe 3: {} Ergebnisse", results.size());
        return results;
    }

    private List<SearchResult> callBrave(String query, String lookupTerm, int count) {
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
            log.trace("[BraveSearch] HTTP {} durationMs={}", response.getStatusCode().value(), duration);
            if (log.isTraceEnabled()) {
                extracted.forEach(r -> log.trace("[BraveSearch]   URL: {} | desc-len={} | snippets={}",
                        r.getUrl(),
                        r.getDescription() != null ? r.getDescription().length() : 0,
                        r.getExtraSnippets() != null ? r.getExtraSnippets().size() : 0));
            }
            return extracted;
        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - start;
            int status = e.getStatusCode().value();
            usageLogService.log(Provider.BRAVE, RequestType.SEARCH, lookupTerm, status, null, null, duration, null);
            if (status == 429) {
                var responseHeaders = e.getResponseHeaders();
                String retryAfterHeader = responseHeaders != null ? responseHeaders.getFirst("Retry-After") : null;
                int retryAfterSeconds = RateLimitException.parseRetryAfter(retryAfterHeader);
                log.warn("Brave search rate limited — retryAfter={}s", retryAfterSeconds);
                throw new RateLimitException(retryAfterSeconds, Provider.BRAVE, null);
            }
            log.warn("Brave search failed for query='{}' (status={}): {}", query, status, e.getMessage());
            return List.of();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("Brave search failed for query='{}': {}", query, e.getMessage());
            usageLogService.log(Provider.BRAVE, RequestType.SEARCH, lookupTerm,
                    500, null, null, duration, null);
            return List.of();
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
