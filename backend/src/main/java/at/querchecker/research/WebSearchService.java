package at.querchecker.research;

import at.querchecker.research.model.SearchResult;

import java.util.List;

/**
 * Quellunabhängiges Interface für Produktsuchen.
 * Implementierungen: BraveWebSearchService (aktiv), zukünftig GoogleWebSearchService.
 * Aktiver Provider: querchecker.api.search.active-provider in application.yml
 */
public interface WebSearchService {

    /**
     * Sucht Treffer für den lookupTerm auf einer bestimmten Domain.
     *
     * @param lookupTerm     Produktname (z.B. "Lenovo ThinkPad X1 Carbon Gen 11")
     * @param siteDomain     Zieldomain (z.B. "icecat.biz", "gsmarena.com")
     * @param keywords       Kategorie-Schlüsselwörter zur Query-Anreicherung (max. 5, darf null sein)
     * @param queryExcludes  Negativ-Operatoren (z.B. ["-filetype:pdf"], darf null sein)
     * @param resultCount    Anzahl Brave-Treffer (10 für Snippets-Pfad, 3 für HTML-Fetch-Pfad)
     * @return Trefferliste (leer wenn alle Stufen keine Ergebnisse liefern)
     */
    List<SearchResult> search(
        String lookupTerm,
        String siteDomain,
        List<String> keywords,
        List<String> queryExcludes,
        int resultCount
    );
}
