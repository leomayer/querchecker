package at.querchecker.controller;

import at.querchecker.api.config.LimitUnit;
import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.api.service.QuotaService;
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

@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
@Tag(name = "Usage", description = "API-Verbrauchsstatistik")
public class ApiUsageController {

    private final ApiUsageLogService usageLogService;
    private final ProviderProperties providerProperties;
    private final QuotaService quotaService;

    @GetMapping
    @Operation(summary = "API-Verbrauchsstatistik des laufenden Kontingent-Zeitraums abrufen")
    public UsageResponse getUsage() {
        return new UsageResponse(
                providerUsage(Provider.BRAVE),
                providerUsage(Provider.GOOGLE_DISCOVERY),
                providerUsage(Provider.GROQ),
                providerUsage(Provider.OPENROUTER)
        );
    }

    private ProviderUsageDto providerUsage(Provider provider) {
        ProviderConfig config = providerProperties.getProvider(provider);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
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
            periodStart = todayStart;
        }

        long calls = usageLogService.countByProviderAndPeriod(provider, periodStart, now);
        long callsToday = usageLogService.countByProviderAndPeriod(provider, todayStart, now);
        long tokensIn = usageLogService.sumTokensInputByProviderAndPeriod(provider, periodStart, now);
        long tokensOut = usageLogService.sumTokensOutputByProviderAndPeriod(provider, periodStart, now);
        long quotaUsage = limitUnit == LimitUnit.TOKENS ? tokensIn + tokensOut : calls;

        return new ProviderUsageDto(calls, callsToday, tokensIn, tokensOut, quotaUsage, quotaLimit);
    }
}
