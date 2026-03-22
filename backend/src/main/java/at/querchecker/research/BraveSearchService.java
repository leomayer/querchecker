package at.querchecker.research;

import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.research.model.BraveApiResponse;
import at.querchecker.research.model.BraveResult;
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
 * Sucht Produktspezifikationen auf Icecat via Brave Search API.
 * Dreistufige Fallback-Strategie: mit Präferenzen → technische Daten → direkte Suche.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BraveSearchService {

    private static final String BRAVE_API_URL = "https://api.search.brave.com/res/v1/web/search";
    private static final String NEGATIVE_FILTERS_STAGES_1_2 =
            " -filetype:pdf -\"user guide\" -\"quick start\" -\"Bedienungsanleitung\"";
    private static final String NEGATIVE_FILTER_STAGE_3 = " -filetype:pdf";

    private final RestTemplate restTemplate;
    private final ApiUsageLogService usageLogService;
    private final ProviderProperties providerProperties;

    /**
     * Sucht Treffer für den lookupTerm auf icecat.biz — dreistufige Query-Strategie.
     *
     * @param lookupTerm         Produktname (z.B. "Lenovo ThinkPad X1 Carbon Gen 11")
     * @param preferenceKeywords Kategorie-Präferenz-Schlüsselwörter (max. 5 werden verwendet)
     * @return Trefferliste (leer wenn alle Stufen keine Ergebnisse liefern)
     */
    public List<BraveResult> search(String lookupTerm, List<String> preferenceKeywords) {
        // Stufe 1: mit Präferenz-Keywords
        List<BraveResult> results = callBrave(buildStage1Query(lookupTerm, preferenceKeywords), lookupTerm);
        if (!results.isEmpty()) return results;

        // Stufe 2: Spezifikationen technische Daten
        results = callBrave(buildStage2Query(lookupTerm), lookupTerm);
        if (!results.isEmpty()) return results;

        // Stufe 3: direkte Suche (letzter Fallback)
        return callBrave(buildStage3Query(lookupTerm), lookupTerm);
    }

    private List<BraveResult> callBrave(String query, String lookupTerm) {
        ProviderConfig config = providerProperties.getProvider(Provider.BRAVE);
        String url = buildUrl(query);

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

    private String buildUrl(String query) {
        return UriComponentsBuilder.fromHttpUrl(BRAVE_API_URL)
                .queryParam("q", query)
                .queryParam("count", 10)
                .queryParam("extra_snippets", true)
                .build()
                .toUriString();
    }

    private String buildStage1Query(String lookupTerm, List<String> preferenceKeywords) {
        StringBuilder q = new StringBuilder(lookupTerm);
        q.append(" Spezifikationen");
        preferenceKeywords.stream().limit(5).forEach(k -> q.append(" ").append(k));
        q.append(" site:icecat.biz");
        q.append(NEGATIVE_FILTERS_STAGES_1_2);
        return q.toString();
    }

    private String buildStage2Query(String lookupTerm) {
        return lookupTerm + " Spezifikationen technische Daten site:icecat.biz"
                + NEGATIVE_FILTERS_STAGES_1_2;
    }

    private String buildStage3Query(String lookupTerm) {
        return lookupTerm + " site:icecat.biz" + NEGATIVE_FILTER_STAGE_3;
    }

    private List<BraveResult> extractResults(BraveApiResponse body) {
        if (body == null || body.getWeb() == null) return List.of();
        return body.getWeb().getResults().stream()
                .map(r -> BraveResult.builder()
                        .title(r.getTitle())
                        .url(r.getUrl())
                        .description(r.getDescription())
                        .extraSnippets(r.getExtraSnippets() != null ? r.getExtraSnippets() : List.of())
                        .build())
                .toList();
    }
}
