package at.querchecker.api.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WebSearchProviderRouter {

    private final SearchProperties searchProperties;
    private final List<WebSearchService> services;

    public WebSearchService getActive() {
        SearchProvider active = searchProperties.getActiveProvider();
        return services.stream()
            .filter(s -> s.getProvider() == active)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Kein WebSearchService für Provider: " + active));
    }
}
