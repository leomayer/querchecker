package at.querchecker.research;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.research.model.IcecatFetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
        String url = UriComponentsBuilder.fromHttpUrl(ICECAT_API_URL)
                .queryParam("icecat_id", icecatId)
                .queryParam("lang", "DE")
                .queryParam("shopname", OPEN_USER)
                .build()
                .toUriString();

        long start = System.currentTimeMillis();
        try {
            String response = restTemplate.getForObject(url, String.class);
            long duration = System.currentTimeMillis() - start;
            usageLogService.log(Provider.ICECAT, RequestType.SPEC_DETAIL,
                    icecatId, 200, null, null, duration);
            return IcecatFetchResult.found(response);
        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - start;
            int status = e.getStatusCode().value();
            log.warn("Icecat fetchFullSpecs failed for id={}: {}", icecatId, e.getMessage());
            usageLogService.log(Provider.ICECAT, RequestType.SPEC_DETAIL,
                    icecatId, status, null, null, duration);
            return e.getStatusCode() == HttpStatus.NOT_FOUND
                    ? IcecatFetchResult.notFound()
                    : IcecatFetchResult.error();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("Icecat fetchFullSpecs failed for id={}: {}", icecatId, e.getMessage());
            usageLogService.log(Provider.ICECAT, RequestType.SPEC_DETAIL,
                    icecatId, 500, null, null, duration);
            return IcecatFetchResult.error();
        }
    }
}
