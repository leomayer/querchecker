package at.querchecker.api.search;

import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
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
        // Stufe 1: mit Präferenz-Keywords + Spezifikationen
        List<SearchResult> results = callBrave(
            buildStage1Query(lookupTerm, siteDomain, keywords, queryExcludes),
            lookupTerm, resultCount);
        if (!results.isEmpty()) return results;

        // Stufe 2: Spezifikationen technische Daten
        results = callBrave(
            buildStage2Query(lookupTerm, siteDomain, queryExcludes),
            lookupTerm, resultCount);
        if (!results.isEmpty()) return results;

        // Stufe 3: direkte Suche (letzter Fallback, keine Negativ-Filter)
        return callBrave(buildStage3Query(lookupTerm, siteDomain), lookupTerm, resultCount);
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
                    response.getStatusCode().value(), null, null, duration);
            log.debug("Brave search query='{}' status={} results={} durationMs={}",
                    query, response.getStatusCode().value(),
                    extractResults(response.getBody()).size(), duration);

            return extractResults(response.getBody());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("Brave search failed for query='{}': {}", query, e.getMessage());
            usageLogService.log(Provider.BRAVE, RequestType.SEARCH, lookupTerm,
                    500, null, null, duration);
            return List.of();
        }
    }

    private String buildUrl(String query, int count) {
        return UriComponentsBuilder.fromHttpUrl(BRAVE_API_URL)
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
                .map(r -> SearchResult.builder()
                        .title(r.getTitle())
                        .url(r.getUrl())
                        .description(r.getDescription())
                        .extraSnippets(r.getExtraSnippets() != null ? r.getExtraSnippets() : List.of())
                        .build())
                .toList();
    }
}
