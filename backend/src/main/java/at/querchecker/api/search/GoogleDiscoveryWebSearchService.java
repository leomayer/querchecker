package at.querchecker.api.search;

import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.research.model.SearchResult;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.discoveryengine.v1.SearchRequest;
import com.google.cloud.discoveryengine.v1.SearchResponse;
import com.google.cloud.discoveryengine.v1.SearchServiceClient;
import com.google.cloud.discoveryengine.v1.SearchServiceSettings;
import com.google.protobuf.Value;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDiscoveryWebSearchService implements WebSearchService {

  private final ProviderProperties providerProperties;
  private final ApiUsageLogService usageLogService;

  @Override
  public SearchProvider getProvider() {
    return SearchProvider.GOOGLE_DISCOVERY;
  }

  @Override
  public List<SearchResult> search(
    String term,
    String domain,
    List<String> keywords,
    List<String> excludes,
    int resultCount
  ) {
    var cfg = providerProperties.getGoogleDiscovery();
    String query = buildQuery(term, domain, keywords, excludes);
    log.trace("[GoogleDiscovery] query='{}', resultCount={}", query, resultCount);
    long start = System.currentTimeMillis();

    try {
      GoogleCredentials credentials = GoogleCredentials.fromStream(
        new FileInputStream(cfg.getCredentialsPath())
      );

      SearchServiceSettings settings = SearchServiceSettings.newBuilder()
        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
        .build();

      try (SearchServiceClient client = SearchServiceClient.create(settings)) {
        String servingConfig = String.format(
          "projects/%s/locations/%s/collections/default_collection/engines/%s/servingConfigs/default_search",
          cfg.getProjectId(),
          cfg.getLocation(),
          cfg.getEngineId()
        );

        SearchRequest.Builder requestBuilder = SearchRequest.newBuilder()
          .setServingConfig(servingConfig)
          .setQuery(query)
          .setPageSize(resultCount);
        if (cfg.getLanguageCode() != null && !cfg.getLanguageCode().isBlank()) {
          requestBuilder.setLanguageCode(cfg.getLanguageCode());
        }
        SearchRequest request = requestBuilder.build();

        var response = client.search(request);

        usageLogService.log(
          Provider.GOOGLE_DISCOVERY,
          RequestType.SEARCH,
          null,
          0,
          null,
          null,
          System.currentTimeMillis() - start,
          null
        );

        return mapSdkResults(response, resultCount);
      }
    } catch (Exception e) {
      log.error("Google Discovery search failed for query='{}': {}", query, e.getMessage());
      return List.of();
    }
  }

  // Matches a locale prefix like /de/, /us/, /ar-sa/ at the start of a URL path
  private static final Pattern LOCALE_PATH_PREFIX = Pattern.compile("^/[a-z]{2}(-[a-z]{2})?/");

  private List<SearchResult> mapSdkResults(
    SearchServiceClient.SearchPagedResponse response,
    int limit
  ) {
    List<SearchResult> results = new ArrayList<>();
    Set<String> seenCanonicalPaths = new HashSet<>();
    boolean firstResult = true;

    for (SearchResponse.SearchResult element : response.iterateAll()) {
      if (results.size() >= limit) break;
      var data = element.getDocument().getDerivedStructData().getFieldsMap();

      if (firstResult) {
        log.debug("[GoogleDiscovery] derivedStructData keys: {}", data.keySet());
        firstResult = false;
      }

      String url = data
        .getOrDefault("link", Value.newBuilder().setStringValue("").build())
        .getStringValue();
      String canonicalPath = canonicalizePath(url);
      if (!seenCanonicalPaths.add(canonicalPath)) {
        log.trace("[GoogleDiscovery] Skipping locale duplicate: {}", url);
        continue;
      }

      String title = data
        .getOrDefault("title", Value.newBuilder().setStringValue("").build())
        .getStringValue();
      String snippet = extractSnippets(data.get("snippets"));

      results.add(new SearchResult(title, url, snippet, List.of()));
    }
    return results;
  }

  /** Strips locale prefix (e.g. /de/, /us/, /ar-sa/) from the URL path for deduplication. */
  private String canonicalizePath(String url) {
    try {
      java.net.URI uri = new java.net.URI(url);
      String path = uri.getHost() + LOCALE_PATH_PREFIX.matcher(uri.getPath()).replaceFirst("/");
      return path;
    } catch (Exception e) {
      return url;
    }
  }

  /**
   * Snippets in Discovery Engine derivedStructData are stored as a LIST_VALUE of STRUCTs,
   * each with a "snippet" string field — not a plain STRING_VALUE.
   */
  private String extractSnippets(Value snippetsValue) {
    if (snippetsValue == null) return "";
    if (snippetsValue.hasStringValue()) return snippetsValue.getStringValue();
    if (snippetsValue.hasListValue()) {
      return snippetsValue
        .getListValue()
        .getValuesList()
        .stream()
        .filter(Value::hasStructValue)
        .map((v) ->
          v
            .getStructValue()
            .getFieldsOrDefault("snippet", Value.newBuilder().setStringValue("").build())
            .getStringValue()
        )
        .filter((s) -> !s.isBlank())
        .collect(Collectors.joining(" "));
    }
    return "";
  }

  private String buildQuery(String term, String domain, List<String> kws, List<String> ex) {
    // Discovery Engine is semantic — language-specific keywords (e.g. German USER fields)
    // hurt results on English-only sites. Use term + site filter + excludes only.
    // Excludes already carry their own '-' prefix (e.g. "-filetype:pdf") — no extra '-' added.
    StringBuilder sb = new StringBuilder(term);
    if (domain != null) sb.append(" site:").append(domain);
    if (ex != null) ex.forEach((e) -> sb.append(" ").append(e));
    return sb.toString();
  }
}
