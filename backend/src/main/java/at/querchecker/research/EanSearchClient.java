package at.querchecker.research;

import at.querchecker.research.config.ResearchConfig;
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
public class EanSearchClient {

    private static final String API_URL = "https://api.ean-search.org/api";
    private static final int LANGUAGE_ANY = 99;

    private final RestTemplate restTemplate;
    private final ResearchConfig config;
    private final ObjectMapper objectMapper;

    public JsonNode search(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("op", "product-search")
                .queryParam("token", config.getEansearch().getToken())
                .queryParam("name", query)
                .queryParam("language", LANGUAGE_ANY)
                .queryParam("format", "json")
                .build()
                .toUriString();
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (HttpClientErrorException e) {
            log.debug("ean-search.org returned {} for query '{}': {}", e.getStatusCode(), query, e.getResponseBodyAsString());
            try {
                return objectMapper.readTree(e.getResponseBodyAsString());
            } catch (Exception parseEx) {
                return objectMapper.createObjectNode()
                        .put("totalproducts", 0)
                        .putArray("productlist");
            }
        }
    }
}
