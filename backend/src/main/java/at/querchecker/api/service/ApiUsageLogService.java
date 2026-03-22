package at.querchecker.api.service;

import at.querchecker.api.entity.ApiUsageLog;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.repository.ApiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Protokolliert jeden echten API-Call — nie bei Cache-Hits.
 * Aggregations-Methoden für den Usage Monitor (Kap. 5).
 */
@Service
@RequiredArgsConstructor
public class ApiUsageLogService {

    private final ApiUsageLogRepository repo;

    public void log(Provider provider,
                    RequestType requestType,
                    String lookupTerm,
                    Integer responseStatus,
                    Integer tokensInput,
                    Integer tokensOutput,
                    Long durationMs) {
        repo.save(ApiUsageLog.builder()
            .provider(provider)
            .requestType(requestType)
            .lookupTerm(lookupTerm)
            .responseStatus(responseStatus)
            .tokensInput(tokensInput)
            .tokensOutput(tokensOutput)
            .durationMs(durationMs)
            .createdAt(LocalDateTime.now())
            .build());
    }

    public long countByProviderAndPeriod(Provider provider, LocalDateTime from, LocalDateTime to) {
        return repo.countByProviderAndCreatedAtBetween(provider, from, to);
    }

    public long sumTokensByProviderAndPeriod(Provider provider, LocalDateTime from, LocalDateTime to) {
        Long result = repo.sumTokensByProviderAndCreatedAtBetween(provider, from, to);
        return result != null ? result : 0L;
    }

    public double avgDurationByProvider(Provider provider, LocalDateTime from, LocalDateTime to) {
        Double result = repo.avgDurationMsByProviderAndCreatedAtBetween(provider, from, to);
        return result != null ? result : 0.0;
    }
}
