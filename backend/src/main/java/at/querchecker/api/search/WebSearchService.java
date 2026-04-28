package at.querchecker.api.search;

import at.querchecker.api.result.ApiCallResult;
import at.querchecker.research.model.SearchResult;

import java.util.List;

/**
 * Quellunabhängiges Interface für Produktsuchen.
 * Implementierungen: BraveWebSearchService, GoogleDiscoveryWebSearchService.
 * Aktiver Provider: querchecker.api.search.active-provider in application.yml
 *
 * Gibt {@link ApiCallResult} zurück statt Exceptions zu werfen:
 * Success → Trefferliste, RateLimited → 429, Unreachable → 503/Timeout, Unavailable → 401/403.
 */
public interface WebSearchService {

    SearchProvider getProvider();

    /** Minimaler Verbindungstest — ruft search() mit harmlosen Parametern auf. */
    default ApiCallResult<Void> testConnection() {
        ApiCallResult<List<SearchResult>> r = search("test", "icecat.biz", null, null, 1);
        return switch (r) {
            case ApiCallResult.Success<List<SearchResult>> ignored -> new ApiCallResult.Success<>(null);
            case ApiCallResult.RateLimited<List<SearchResult>> rl  -> new ApiCallResult.RateLimited<>(rl.retryAfterSeconds());
            case ApiCallResult.Unreachable<List<SearchResult>> u   -> new ApiCallResult.Unreachable<>(u.reason(), u.httpStatus());
            case ApiCallResult.Unavailable<List<SearchResult>> u   -> new ApiCallResult.Unavailable<>(u.reason(), u.httpStatus());
        };
    }

    /**
     * Sucht Treffer für den lookupTerm auf einer bestimmten Domain.
     *
     * @param lookupTerm     Produktname (z.B. "Lenovo ThinkPad X1 Carbon Gen 11")
     * @param siteDomain     Zieldomain (z.B. "icecat.biz", "gsmarena.com")
     * @param keywords       Kategorie-Schlüsselwörter zur Query-Anreicherung (max. 5, darf null sein)
     * @param queryExcludes  Negativ-Operatoren (z.B. ["-filetype:pdf"], darf null sein)
     * @param resultCount    Anzahl Treffer (10 für Snippets-Pfad, 3 für HTML-Fetch-Pfad)
     */
    ApiCallResult<List<SearchResult>> search(
        String lookupTerm,
        String siteDomain,
        List<String> keywords,
        List<String> queryExcludes,
        int resultCount
    );
}
