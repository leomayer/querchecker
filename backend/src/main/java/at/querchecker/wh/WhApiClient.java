package at.querchecker.wh;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * Kapselt alle authentifizierten HTTP-Aufrufe zur Willhaben-API.
 * Holt den Application-Token einmalig und cached ihn für die Lebenszeit der Instanz.
 */
@Component
@RequiredArgsConstructor
public class WhApiClient {

    private static final String WH_TOKEN_URL =
        "https://www.willhaben.at/webapi/dac/version/config/web";
    private static final String WH_CLIENT =
        "api@willhaben.at;responsive_web;server;1.0.0;";
    private static final String USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";

    private final RestTemplate restTemplate;

    private volatile String applicationToken;

    public <T> ResponseEntity<T> get(URI uri, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, "application/json");
        headers.set("X-WH-Client", WH_CLIENT);
        headers.set("X-WH-Application-Token", fetchApplicationToken());
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private String fetchApplicationToken() {
        if (applicationToken != null) return applicationToken;
        synchronized (this) {
            if (applicationToken != null) return applicationToken;
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            ResponseEntity<String> resp = restTemplate.exchange(
                WH_TOKEN_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            applicationToken = resp.getBody() != null ? resp.getBody().trim() : "";
            return applicationToken;
        }
    }
}
