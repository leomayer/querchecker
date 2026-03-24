package at.querchecker.research;

import at.querchecker.config.UserAgentHolder;
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

    private final UserAgentHolder userAgentHolder;

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
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgentHolder.get())
                    .timeout(TIMEOUT_MS)
                    .get();

            String text = switch (sourceType) {
                case GSMARENA     -> extractGsmarena(doc);
                case FLATPANELSHD -> extractFlatpanelsHd(doc);
                default           -> doc.body().text();
            };

            return text.isBlank() ? Optional.empty() : Optional.of(text);

        } catch (IOException e) {
            log.warn("HtmlFetch fehlgeschlagen für {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    // GSMArena: Spec-Tabelle hat class="specs-phone-big-table"
    private String extractGsmarena(Document doc) {
        return doc.select("table.specs-phone-big-table").text();
    }

    // FlatpanelsHD: Spec-Tabellen — Selektoren ggf. nach erstem Live-Test anpassen
    private String extractFlatpanelsHd(Document doc) {
        String tables = doc.select("table.specsTable, div.specs, table.tv-specs").text();
        return tables.isBlank() ? doc.select("main").text() : tables;
    }
}
