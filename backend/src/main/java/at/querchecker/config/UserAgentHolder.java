package at.querchecker.config;

import org.springframework.stereotype.Component;

/**
 * Speichert den User-Agent des ersten Browser-Requests.
 * HtmlFetchService verwendet diesen für Jsoup-Fetches, damit GSMArena/FlatpanelsHD
 * den Request nicht als Bot erkennen. Fallback: fest kodierter Chrome-UA.
 */
@Component
public class UserAgentHolder {

    private static final String DEFAULT_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private volatile String cachedUserAgent = null;

    public void capture(String userAgent) {
        if (cachedUserAgent == null && userAgent != null && !userAgent.isBlank()) {
            cachedUserAgent = userAgent;
        }
    }

    public String get() {
        return cachedUserAgent != null ? cachedUserAgent : DEFAULT_UA;
    }
}
