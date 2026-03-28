package at.querchecker.api.search;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Konfiguration für den aktiven Web-Search-Provider.
 * Wert: querchecker.api.search.active-provider in application.yml
 */
@Component
@ConfigurationProperties(prefix = "querchecker.api.search")
@Data
public class SearchProperties {

    /** Aktiver Search-Provider: BRAVE | GOOGLE_DISCOVERY — Neustart nötig bei Wechsel */
    private SearchProvider activeProvider = SearchProvider.BRAVE;
}
