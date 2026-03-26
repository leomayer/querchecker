package at.querchecker.research;

import at.querchecker.config.RequestUserAgentResolver;
import at.querchecker.research.entity.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HtmlFetchService {

    private static final int TIMEOUT_MS = 10_000;

    private final RequestUserAgentResolver uaResolver;

    /**
     * Entscheidet ob für einen SourceType ein vollständiger HTML-Fetch gemacht wird.
     * FLATPANELSHD und GSMARENA liefern strukturierten Spec-Content der via Jsoup extrahiert wird.
     * ICECAT und GENERIC verwenden den Snippets-Pfad (Brave-Snippets direkt ans LLM).
     */
    public boolean shouldFetchFullPage(SourceType sourceType) {
        return sourceType == SourceType.FLATPANELSHD
                || sourceType == SourceType.GSMARENA;
    }

    /**
     * Lädt eine URL via Jsoup und extrahiert den relevanten Spec-Content.
     * Gibt bereinigten Text zurück — direkt als LLM-Input verwendbar.
     * Bei Netzwerkfehler oder leerem Ergebnis: Optional.empty().
     */
    public Optional<String> fetchAndExtract(String url, SourceType sourceType) {
        try {
            log.info("[HtmlFetch] Fetching {} from {}", sourceType, url);
            Document doc = Jsoup.connect(url)
                    .userAgent(uaResolver.resolve())
                    .timeout(TIMEOUT_MS)
                    .get();

            log.debug("[HtmlFetch] Document size: {} bytes, title: {}", doc.html().length(), doc.title());

            String text = switch (sourceType) {
                case GSMARENA     -> extractGsmarena(doc);
                case FLATPANELSHD -> extractFlatpanelsHd(doc);
                default           -> doc.body().text();
            };

            log.info("[HtmlFetch] Extracted {} chars for {}", text.length(), sourceType);
            return text.isBlank() ? Optional.empty() : Optional.of(text);

        } catch (IOException e) {
            log.warn("[HtmlFetch] Failed for {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    // GSMArena: Spec-Tabelle hat class="specs-phone-big-table"
    private String extractGsmarena(Document doc) {
        String selected = doc.select("table.specs-phone-big-table").text();
        log.debug("[HtmlFetch.GSMArena] Found specs-phone-big-table: {} chars", selected.length());
        if (selected.isBlank()) {
            String allTables = doc.select("table").text();
            log.debug("[HtmlFetch.GSMArena] specs-phone-big-table empty, trying all tables: {} chars", allTables.length());
            return allTables;
        }
        return selected;
    }

    // FlatpanelsHD: Spec-Tabellen — Selektoren ggf. nach erstem Live-Test anpassen
    private String extractFlatpanelsHd(Document doc) {
        String tables = doc.select("table.specsTable, div.specs, table.tv-specs").text();
        log.debug("[HtmlFetch.FlatpanelsHD] Found spec tables: {} chars", tables.length());
        if (tables.isBlank()) {
            String main = doc.select("main").text();
            log.debug("[HtmlFetch.FlatpanelsHD] Spec tables empty, using main: {} chars", main.length());
            return main;
        }
        return tables;
    }
}
