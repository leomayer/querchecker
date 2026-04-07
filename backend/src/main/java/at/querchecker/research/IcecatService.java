package at.querchecker.research;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.research.model.IcecatFetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Lädt vollständige Produktspezifikationen von Icecat (kein LLM-Extrakt).
 * Gecacht in ProductLookup.icecatSpecsJson.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IcecatService {

    private static final String ICECAT_API_URL = "https://live.icecat.biz/api";
    private static final String OPEN_USER = "openIcecat-live";

    private final RestTemplate restTemplate;
    private final ApiUsageLogService usageLogService;

    /**
     * Lädt alle Specs für eine Icecat-Produkt-ID.
     *
     * @param icecatId Icecat-Produkt-ID (aus URL-Pattern: .../p/[slug]-[icecatId].html)
     * @return {@link IcecatFetchResult} — unterscheidet "gefunden", "404 nicht gefunden" und "Fehler"
     */
    public IcecatFetchResult fetchFullSpecs(String icecatId) {
        String url = UriComponentsBuilder.fromUriString(ICECAT_API_URL)
                .queryParam("icecat_id", icecatId)
                .queryParam("lang", "DE")
                .queryParam("shopname", OPEN_USER)
                .build()
                .toUriString();

        // Use Accept: */* so StringHttpMessageConverter handles the response body as raw text.
        // Without this, Jackson is selected for application/json responses and fails to parse
        // a JSON object into String.class (Jackson expects a JSON string literal, not an object).
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.ALL));
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        log.info("[Icecat] Fetching full specs for icecatId={}", icecatId);
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            String response = responseEntity.getBody();
            long duration = System.currentTimeMillis() - start;
            log.info("[Icecat] Fetch OK for icecatId={}, duration={}ms, bodyLength={}",
                    icecatId, duration, response != null ? response.length() : 0);
            usageLogService.log(Provider.ICECAT, RequestType.SPEC_DETAIL,
                    icecatId, 200, null, null, duration, null);
            return IcecatFetchResult.found(response);
        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - start;
            int status = e.getStatusCode().value();
            log.warn("Icecat fetchFullSpecs failed for id={}: {}", icecatId, e.getMessage());
            usageLogService.log(Provider.ICECAT, RequestType.SPEC_DETAIL,
                    icecatId, status, null, null, duration, null);
            return e.getStatusCode() == HttpStatus.NOT_FOUND
                    ? IcecatFetchResult.notFound()
                    : IcecatFetchResult.error();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("Icecat fetchFullSpecs failed for id={}: {}", icecatId, e.getMessage());
            usageLogService.log(Provider.ICECAT, RequestType.SPEC_DETAIL,
                    icecatId, 500, null, null, duration, null);
            return IcecatFetchResult.error();
        }
    }
}
