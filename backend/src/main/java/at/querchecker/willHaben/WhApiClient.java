package at.querchecker.willHaben;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kapselt alle authentifizierten HTTP-Aufrufe zur Willhaben-API.
 * Holt den Application-Token einmalig und cached ihn für die Lebenszeit der Instanz.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhApiClient {

    private static final String WH_TOKEN_URL =
        "https://www.willhaben.at/webapi/dac/version/config/web";
    private static final String WH_HOME_URL =
        "https://www.willhaben.at/";
    private static final String WH_CLIENT =
        "api@willhaben.at;responsive_web;server;1.0.0;";
    private static final String USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";
    private static final Pattern BUILD_ID_PATTERN =
        Pattern.compile("\"buildId\":\"([^\"]+)\"");

    private final RestTemplate restTemplate;

    private volatile String applicationToken;
    private volatile String buildId;

    public <T> ResponseEntity<T> get(URI uri, Class<T> responseType) {
        try {
            return doGet(uri, responseType);
        } catch (HttpClientErrorException.BadRequest e) {
            applicationToken = null; // stale token — force refresh
            return doGet(uri, responseType);
        }
    }

    private <T> ResponseEntity<T> doGet(URI uri, Class<T> responseType) {
        log.info("GET {}", uri);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, "application/json");
        headers.set("X-WH-Client", WH_CLIENT);
        headers.set("X-WH-Application-Token", fetchApplicationToken());
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    /**
     * Fetches `/_next/data/{buildId}/iad/{seoUrl}.json` from willhaben.at.
     * Automatically refreshes the cached buildId if WH deploys a new version (404).
     */
    public <T> ResponseEntity<T> getNextData(String seoUrl, Class<T> responseType) {
        try {
            return doGetNextData(seoUrl, responseType);
        } catch (HttpClientErrorException.NotFound e) {
            buildId = null; // stale buildId — force refresh
            return doGetNextData(seoUrl, responseType);
        }
    }

    private <T> ResponseEntity<T> doGetNextData(String seoUrl, Class<T> responseType) {
        String cleanSeo = seoUrl.endsWith("/") ? seoUrl.substring(0, seoUrl.length() - 1) : seoUrl;
        try {
            URI uri = new URI("https", "www.willhaben.at",
                "/_next/data/" + fetchBuildId() + "/iad/" + cleanSeo + ".json", null, null);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, "application/json");
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
        } catch (Exception e) {
            if (e instanceof HttpClientErrorException.NotFound nfe) throw nfe;
            throw new RuntimeException("Next.js data fetch failed for seoUrl=" + seoUrl, e);
        }
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
            log.info("Fetched applicationToken (length={}, preview={})",
                applicationToken.length(),
                applicationToken.length() > 80 ? applicationToken.substring(0, 80) + "..." : applicationToken);
            return applicationToken;
        }
    }

    private String fetchBuildId() {
        if (buildId != null) return buildId;
        synchronized (this) {
            if (buildId != null) return buildId;
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, "text/html");
            ResponseEntity<String> resp = restTemplate.exchange(
                WH_HOME_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            String body = resp.getBody() != null ? resp.getBody() : "";
            Matcher m = BUILD_ID_PATTERN.matcher(body);
            buildId = m.find() ? m.group(1) : "unknown";
            return buildId;
        }
    }
}
