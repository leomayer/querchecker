package at.querchecker.research;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.service.ApiUsageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
     * Lädt alle Specs für eine Icecat-Produkt-ID als JSON-String.
     *
     * @param icecatId Icecat-Produkt-ID (aus URL-Pattern: .../p/[slug]-[icecatId].html)
     * @return vollständiger Icecat-Response als JSON-String, oder null bei Fehler
     */
    public String fetchFullSpecs(String icecatId) {
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
            usageLogService.log(Provider.GOOGLE, RequestType.SPEC_DETAIL,
                    icecatId, 200, null, null, duration);
            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("Icecat fetchFullSpecs failed for id={}: {}", icecatId, e.getMessage());
            usageLogService.log(Provider.GOOGLE, RequestType.SPEC_DETAIL,
                    icecatId, 500, null, null, duration);
            return null;
        }
    }
}
