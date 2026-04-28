package at.querchecker.controller;

import at.querchecker.api.config.LimitUnit;
import at.querchecker.api.config.LlmProperties;
import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.search.SearchProperties;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.api.service.QuotaService;
import at.querchecker.controller.dto.ModelUsageDto;
import at.querchecker.controller.dto.ProviderUsageDto;
import at.querchecker.controller.dto.UsageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
@Tag(name = "Usage", description = "API-Verbrauchsstatistik")
public class ApiUsageController {

    private final ApiUsageLogService usageLogService;
    private final ProviderProperties providerProperties;
    private final QuotaService quotaService;
    private final SearchProperties searchProperties;
    private final LlmProperties llmProperties;

    @GetMapping
    @Operation(summary = "API-Verbrauchsstatistik des laufenden Kontingent-Zeitraums abrufen")
    public UsageResponse getUsage() {
        LocalDateTime now = LocalDateTime.now();
        ProviderUsageDto groqUsage = providerUsage(Provider.GROQ, modelName(Provider.GROQ), now);
        List<ModelUsageDto> groqBreakdown = buildGroqModelBreakdown(now);

        return new UsageResponse(
                searchProperties.getActiveProvider().name(),
                llmProperties.getActiveProvider().name(),
                providerUsage(Provider.BRAVE, null, now),
                providerUsage(Provider.GOOGLE_DISCOVERY, null, now),
                groqUsage,
                providerUsage(Provider.OPENROUTER, modelName(Provider.OPENROUTER), now),
                groqBreakdown
        );
    }

    private String modelName(Provider provider) {
        ProviderConfig config = providerProperties.getProvider(provider);
        return config != null ? config.getModel() : null;
    }

    private ProviderUsageDto providerUsage(Provider provider, String model, LocalDateTime now) {
        ProviderConfig config = providerProperties.getProvider(provider);

        LocalDateTime periodStart;
        long quotaLimit = 0;
        LimitUnit limitUnit = LimitUnit.REQUESTS;

        if (config != null && config.getFreeLimit() > 0) {
            periodStart = quotaService.getPeriodStart(
                    config.getFreeLimitPeriod(), config.getPeriodStartDay()
            ).atStartOfDay();
            quotaLimit = config.getFreeLimit();
            limitUnit = config.getLimitUnit();
        } else {
            periodStart = LocalDate.now().atStartOfDay();
        }

        long calls = usageLogService.countByProviderAndPeriod(provider, periodStart, now);
        long tokensIn = usageLogService.sumTokensInputByProviderAndPeriod(provider, periodStart, now);
        long tokensOut = usageLogService.sumTokensOutputByProviderAndPeriod(provider, periodStart, now);
        long quotaUsage = limitUnit == LimitUnit.TOKENS ? tokensIn + tokensOut : calls;
        String quotaPeriod = (config != null && config.getFreeLimit() > 0)
                ? config.getFreeLimitPeriod().name()
                : null;
        long rateLimitCount = usageLogService.countRateLimitsByProviderAndPeriod(provider, periodStart, now);
        long rateLimitEstimatedTokens = usageLogService.sumEstimatedTokensForRateLimitsByProviderAndPeriod(provider, periodStart, now);

        return new ProviderUsageDto(calls, tokensIn, tokensOut, quotaUsage, quotaLimit, model, quotaPeriod,
                rateLimitCount, rateLimitEstimatedTokens);
    }

    private List<ModelUsageDto> buildGroqModelBreakdown(LocalDateTime now) {
        ProviderConfig config = providerProperties.getProvider(Provider.GROQ);
        if (config == null) return List.of();

        LocalDateTime periodStart = (config.getFreeLimit() > 0)
                ? quotaService.getPeriodStart(config.getFreeLimitPeriod(), config.getPeriodStartDay()).atStartOfDay()
                : LocalDate.now().atStartOfDay();

        List<String> models = new ArrayList<>();
        if (config.getModel() != null && !config.getModel().isBlank()) models.add(config.getModel());
        if (config.getModelLookupSecondary() != null && !config.getModelLookupSecondary().isBlank()
                && !config.getModelLookupSecondary().equals(config.getModel())) {
            models.add(config.getModelLookupSecondary());
        }

        return models.stream()
                .map(m -> new ModelUsageDto(
                        m,
                        usageLogService.countByProviderAndModelNameAndPeriod(Provider.GROQ, m, periodStart, now),
                        usageLogService.sumTokensInByProviderAndModelNameAndPeriod(Provider.GROQ, m, periodStart, now),
                        usageLogService.sumTokensOutByProviderAndModelNameAndPeriod(Provider.GROQ, m, periodStart, now)
                ))
                .toList();
    }
}
