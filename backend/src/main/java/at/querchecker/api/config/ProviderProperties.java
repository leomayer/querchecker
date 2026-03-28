package at.querchecker.api.config;

import at.querchecker.api.entity.Provider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * API-Provider-Konfiguration.
 * Kontingente & Limits in config/querchecker.yml,
 * API-Keys in config/secrets.yml (nie in Git).
 *
 * Zugriff per Provider-Enum: providerProperties.getProvider(Provider.BRAVE)
 */
@Component
@ConfigurationProperties(prefix = "querchecker.api")
@Data
public class ProviderProperties {

    private Map<String, ProviderConfig> limits = new HashMap<>();
    private GoogleDiscoveryConfig googleDiscovery;

    /**
     * Liefert die Konfiguration für einen Provider per Enum-Key.
     * Mapping: Provider.BRAVE → limits["brave"]
     */
    public ProviderConfig getProvider(Provider provider) {
        return limits.get(provider.name().toLowerCase());
    }

    @Data
    public static class GoogleDiscoveryConfig {
        private String projectId;
        private String location;
        private String engineId;
        private String credentialsPath;
    }
}
