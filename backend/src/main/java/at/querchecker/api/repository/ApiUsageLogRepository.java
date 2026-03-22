package at.querchecker.api.repository;

import at.querchecker.api.entity.ApiUsageLog;
import at.querchecker.api.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

    long countByProviderAndCreatedAtBetween(Provider provider, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(l.tokensInput + l.tokensOutput), 0) " +
           "FROM ApiUsageLog l " +
           "WHERE l.provider = :provider " +
           "AND l.createdAt BETWEEN :from AND :to " +
           "AND l.tokensInput IS NOT NULL")
    Long sumTokensByProviderAndCreatedAtBetween(
        @Param("provider") Provider provider,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("SELECT AVG(l.durationMs) FROM ApiUsageLog l " +
           "WHERE l.provider = :provider AND l.createdAt BETWEEN :from AND :to")
    Double avgDurationMsByProviderAndCreatedAtBetween(
        @Param("provider") Provider provider,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);
}
