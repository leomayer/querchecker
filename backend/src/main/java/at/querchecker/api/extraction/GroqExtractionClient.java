package at.querchecker.api.extraction;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.service.ApiUsageLogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ExtractionClient-Implementierung für Groq (llama-3.x).
 * Endpunkt: https://api.groq.com/openai/v1/chat/completions
 */
@Component
public class GroqExtractionClient extends AbstractLlmExtractionClient {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${querchecker.api.limits.groq.model:}")
    private String model;

    @Value("${querchecker.api.limits.groq.api-key:}")
    private String apiKey;

    public GroqExtractionClient(@Qualifier("groqRestClient") RestClient restClient,
                                 ApiUsageLogService usageLogService) {
        super(restClient, usageLogService);
    }

    @Override
    public Provider getProvider() { return Provider.GROQ; }

    @Override
    protected String getApiKeyInfo() {
        if (apiKey == null || apiKey.isBlank()) return "MISSING/EMPTY";
        return apiKey.length() + " chars, prefix='" + apiKey.substring(0, Math.min(4, apiKey.length())) + "...'";
    }

    @Override
    protected String getEndpointUrl() { return ENDPOINT; }

    @Override
    protected String getModel() { return model; }
}
