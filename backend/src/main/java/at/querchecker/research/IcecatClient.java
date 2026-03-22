package at.querchecker.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class IcecatClient {

    private static final String ICECAT_API_URL = "https://live.icecat.biz/api/";
    private static final String OPEN_USER = "openIcecat-live";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public JsonNode getByGtin(String ean) {
        String url = UriComponentsBuilder.fromHttpUrl(ICECAT_API_URL)
                .queryParam("UserName", OPEN_USER)
                .queryParam("Language", "DE")
                .queryParam("GTIN", ean)
                .build()
                .toUriString();
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (HttpClientErrorException e) {
            // Icecat returns 400 when GTIN is not in the open catalog — forward the body as-is
            // so the frontend can display the "not found" state rather than a 500 error.
            log.debug("Icecat returned {} for EAN {}: {}", e.getStatusCode(), ean, e.getResponseBodyAsString());
            try {
                return objectMapper.readTree(e.getResponseBodyAsString());
            } catch (Exception parseEx) {
                return objectMapper.createObjectNode().put("msg", "not found").put("code", -1).putNull("data");
            }
        }
    }
}
