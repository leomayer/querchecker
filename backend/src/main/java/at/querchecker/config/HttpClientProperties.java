package at.querchecker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HTTP client timeout configuration for external API calls
 * (Groq, OpenRouter, Brave, Icecat, etc.)
 */
@Data
@Component
@ConfigurationProperties(prefix = "querchecker.http")
public class HttpClientProperties {
    /** TCP connection timeout in milliseconds */
    private long connectTimeoutMs = 10000;

    /** Read timeout for full response in milliseconds */
    private long readTimeoutMs = 30000;
}
