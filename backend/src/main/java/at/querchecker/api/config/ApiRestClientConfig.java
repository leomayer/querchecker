package at.querchecker.api.config;

import at.querchecker.api.entity.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Erstellt vorkonfigurierte RestClient-Beans für LLM-Provider (Groq, OpenRouter).
 * API-Keys werden aus ProviderProperties gelesen (secrets.yml).
 */
@Configuration
@RequiredArgsConstructor
public class ApiRestClientConfig {

    private final ProviderProperties providerProperties;

    @Bean("groqRestClient")
    public RestClient groqRestClient() {
        String apiKey = providerProperties.getProvider(Provider.GROQ).getApiKey();
        return RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean("openRouterRestClient")
    public RestClient openRouterRestClient() {
        String apiKey = providerProperties.getProvider(Provider.OPENROUTER).getApiKey();
        return RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("HTTP-Referer", "https://querchecker.at")
                .build();
    }
}
