package at.querchecker.controller;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.controller.dto.ProviderUsageDto;
import at.querchecker.controller.dto.UsageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
@Tag(name = "Usage", description = "API-Verbrauchsstatistik (laufender Monat)")
public class ApiUsageController {

    private final ApiUsageLogService usageLogService;

    @GetMapping
    @Operation(summary = "API-Verbrauchsstatistik des laufenden Monats abrufen")
    public UsageResponse getUsage() {
        LocalDateTime from = LocalDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime to = LocalDateTime.now();

        return new UsageResponse(
                providerUsage(Provider.BRAVE, from, to),
                providerUsage(Provider.GROQ, from, to),
                providerUsage(Provider.OPENROUTER, from, to)
        );
    }

    private ProviderUsageDto providerUsage(Provider provider, LocalDateTime from, LocalDateTime to) {
        long calls = usageLogService.countByProviderAndPeriod(provider, from, to);
        double avgMs = usageLogService.avgDurationByProvider(provider, from, to);
        return new ProviderUsageDto(calls, avgMs);
    }
}
