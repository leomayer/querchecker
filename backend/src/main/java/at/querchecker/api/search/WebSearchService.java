package at.querchecker.api.search;

import at.querchecker.research.model.SearchResult;

import java.util.List;

/**
 * Quellunabhängiges Interface für Produktsuchen.
 * Implementierungen: BraveWebSearchService, GoogleDiscoveryWebSearchService.
 * Aktiver Provider: querchecker.api.search.active-provider in application.yml
 */
public interface WebSearchService {

    SearchProvider getProvider();

    /**
     * Sucht Treffer für den lookupTerm auf einer bestimmten Domain.
     *
     * @param lookupTerm     Produktname (z.B. "Lenovo ThinkPad X1 Carbon Gen 11")
     * @param siteDomain     Zieldomain (z.B. "icecat.biz", "gsmarena.com")
     * @param keywords       Kategorie-Schlüsselwörter zur Query-Anreicherung (max. 5, darf null sein)
     * @param queryExcludes  Negativ-Operatoren (z.B. ["-filetype:pdf"], darf null sein)
     * @param resultCount    Anzahl Treffer (10 für Snippets-Pfad, 3 für HTML-Fetch-Pfad)
     * @return Trefferliste (leer wenn keine Ergebnisse geliefert werden)
     */
    List<SearchResult> search(
        String lookupTerm,
        String siteDomain,
        List<String> keywords,
        List<String> queryExcludes,
        int resultCount
    );
}
