package at.querchecker.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UsageResponse {
    private ProviderUsageDto brave;
    private ProviderUsageDto googleDiscovery;
    private ProviderUsageDto groq;
    private ProviderUsageDto openRouter;
}
