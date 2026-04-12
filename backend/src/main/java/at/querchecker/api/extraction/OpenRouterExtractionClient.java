package at.querchecker.api.extraction;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.config.ProviderStatusService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ExtractionClient-Implementierung für OpenRouter.
 * Endpunkt: https://openrouter.ai/api/v1/chat/completions
 */
@Component
public class OpenRouterExtractionClient extends AbstractLlmExtractionClient {

    private static final String ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";

    @Value("${querchecker.api.limits.openrouter.model:}")
    private String model;

    public OpenRouterExtractionClient(@Qualifier("openRouterRestClient") RestClient restClient,
                                      ApiUsageLogService usageLogService,
                                      ProviderStatusService providerStatusService) {
        super(restClient, usageLogService, providerStatusService);
    }

    @Override
    public Provider getProvider() { return Provider.OPENROUTER; }

    @Override
    protected String getEndpointUrl() { return ENDPOINT; }

    @Override
    protected String getModel() { return model; }
}
