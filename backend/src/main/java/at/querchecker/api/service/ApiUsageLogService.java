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
                    Long durationMs,
                    String modelName) {
        repo.save(ApiUsageLog.builder()
            .provider(provider)
            .requestType(requestType)
            .lookupTerm(lookupTerm)
            .responseStatus(responseStatus)
            .tokensInput(tokensInput)
            .tokensOutput(tokensOutput)
            .durationMs(durationMs)
            .modelName(modelName)
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

    public long sumTokensInputByProviderAndPeriod(Provider provider, LocalDateTime from, LocalDateTime to) {
        Long result = repo.sumTokensInputByProviderAndCreatedAtBetween(provider, from, to);
        return result != null ? result : 0L;
    }

    public long sumTokensOutputByProviderAndPeriod(Provider provider, LocalDateTime from, LocalDateTime to) {
        Long result = repo.sumTokensOutputByProviderAndCreatedAtBetween(provider, from, to);
        return result != null ? result : 0L;
    }

    /** Anzahl HTTP-429-Antworten (Rate-Limit-Hits) im Zeitraum */
    public long countRateLimitsByProviderAndPeriod(Provider provider, LocalDateTime from, LocalDateTime to) {
        return repo.countByProviderAndResponseStatusAndCreatedAtBetween(provider, 429, from, to);
    }

    /** Summe der geschätzten Input-Tokens aus rate-limiteten Calls (responseStatus=429) */
    public long sumEstimatedTokensForRateLimitsByProviderAndPeriod(Provider provider, LocalDateTime from, LocalDateTime to) {
        Long result = repo.sumTokensInputByProviderAndResponseStatusAndCreatedAtBetween(provider, 429, from, to);
        return result != null ? result : 0L;
    }

    /** Anzahl Calls pro Modell */
    public long countByProviderAndModelNameAndPeriod(Provider provider, String modelName, LocalDateTime from, LocalDateTime to) {
        return repo.countByProviderAndModelNameAndCreatedAtBetween(provider, modelName, from, to);
    }

    /** Summe Input-Tokens pro Modell (nur erfolgreiche Calls) */
    public long sumTokensInByProviderAndModelNameAndPeriod(Provider provider, String modelName, LocalDateTime from, LocalDateTime to) {
        Long result = repo.sumTokensInputByProviderAndModelNameAndCreatedAtBetween(provider, modelName, from, to);
        return result != null ? result : 0L;
    }

    /** Summe Output-Tokens pro Modell */
    public long sumTokensOutByProviderAndModelNameAndPeriod(Provider provider, String modelName, LocalDateTime from, LocalDateTime to) {
        Long result = repo.sumTokensOutputByProviderAndModelNameAndCreatedAtBetween(provider, modelName, from, to);
        return result != null ? result : 0L;
    }
}
