package at.querchecker.api.config;

import at.querchecker.api.entity.Provider;
import at.querchecker.config.HttpClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Erstellt vorkonfigurierte RestClient-Beans für LLM-Provider (Groq, OpenRouter).
 * API-Keys werden aus ProviderProperties gelesen (secrets.yml).
 * HTTP-Timeouts aus HttpClientProperties (querchecker.http.*).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ApiRestClientConfig {

    private final ProviderProperties providerProperties;
    private final HttpClientProperties httpClientProperties;

    @Bean("groqRestClient")
    public RestClient groqRestClient() {
        String apiKey = providerProperties.getProvider(Provider.GROQ).getApiKey();
        log.info("[ApiRestClientConfig] Groq API key: {}",
                apiKey == null || apiKey.isBlank() ? "MISSING/EMPTY" : apiKey.length() + " chars, prefix='" + apiKey.substring(0, Math.min(4, apiKey.length())) + "...'");
        return RestClient.builder()
                .requestFactory(createHttpRequestFactory())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean("openRouterRestClient")
    public RestClient openRouterRestClient() {
        String apiKey = providerProperties.getProvider(Provider.OPENROUTER).getApiKey();
        return RestClient.builder()
                .requestFactory(createHttpRequestFactory())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("HTTP-Referer", "https://querchecker.at")
                .build();
    }

    private JdkClientHttpRequestFactory createHttpRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(httpClientProperties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(httpClientProperties.getReadTimeoutMs()));
        return factory;
    }
}
