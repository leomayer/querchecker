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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

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
    public List<SearchResult> search(String term, String domain,
                                     List<String> keywords, List<String> excludes,
                                     int resultCount) {
        var cfg = providerProperties.getGoogleDiscovery();
        String query = buildQuery(term, domain, keywords, excludes);
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

                SearchRequest request = SearchRequest.newBuilder()
                    .setServingConfig(servingConfig)
                    .setQuery(query)
                    .setPageSize(resultCount)
                    .build();

                var response = client.search(request);

                usageLogService.log(Provider.GOOGLE_DISCOVERY, RequestType.SEARCH, null, 0, null, null,
                    System.currentTimeMillis() - start);

                return mapSdkResults(response);
            }
        } catch (Exception e) {
            log.error("Google Discovery search failed for query='{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private List<SearchResult> mapSdkResults(SearchServiceClient.SearchPagedResponse response) {
        List<SearchResult> results = new ArrayList<>();
        for (SearchResponse.SearchResult element : response.iterateAll()) {
            var data = element.getDocument().getDerivedStructData().getFieldsMap();
            results.add(new SearchResult(
                data.getOrDefault("title", Value.newBuilder().setStringValue("").build()).getStringValue(),
                data.getOrDefault("link", Value.newBuilder().setStringValue("").build()).getStringValue(),
                data.getOrDefault("snippets", Value.newBuilder().setStringValue("").build()).getStringValue(),
                List.of()
            ));
        }
        return results;
    }

    private String buildQuery(String term, String domain, List<String> kws, List<String> ex) {
        StringBuilder sb = new StringBuilder(term);
        if (domain != null) sb.append(" site:").append(domain);
        if (kws != null) kws.forEach(k -> sb.append(" ").append(k));
        if (ex != null) ex.forEach(e -> sb.append(" -").append(e));
        return sb.toString();
    }
}
