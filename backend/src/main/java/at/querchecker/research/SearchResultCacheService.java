package at.querchecker.research;

import at.querchecker.research.model.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for web search results.
 * Used to avoid re-fetching when retrying after a rate-limit error.
 * Provider-agnostic — survives switching from Brave to Google Discovery.
 */
@Service
public class SearchResultCacheService {

    private final ConcurrentHashMap<String, List<SearchResult>> cache = new ConcurrentHashMap<>();

    public void put(String lookupTerm, String sourceDomain, List<SearchResult> results) {
        cache.put(cacheKey(lookupTerm, sourceDomain), results);
    }

    /** Returns cached results, or null if not present. */
    public List<SearchResult> get(String lookupTerm, String sourceDomain) {
        return cache.get(cacheKey(lookupTerm, sourceDomain));
    }

    /** Removes all cached entries for the given lookup term. */
    public void evict(String lookupTerm) {
        cache.keySet().removeIf(k -> k.startsWith(lookupTerm + "|"));
    }

    private String cacheKey(String lookupTerm, String sourceDomain) {
        return lookupTerm + "|" + sourceDomain;
    }
}
