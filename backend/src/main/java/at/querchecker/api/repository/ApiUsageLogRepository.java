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

    @Query("SELECT COALESCE(SUM(l.tokensInput), 0) FROM ApiUsageLog l " +
           "WHERE l.provider = :provider AND l.createdAt BETWEEN :from AND :to AND l.tokensInput IS NOT NULL")
    Long sumTokensInputByProviderAndCreatedAtBetween(
        @Param("provider") Provider provider,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(l.tokensOutput), 0) FROM ApiUsageLog l " +
           "WHERE l.provider = :provider AND l.createdAt BETWEEN :from AND :to AND l.tokensOutput IS NOT NULL")
    Long sumTokensOutputByProviderAndCreatedAtBetween(
        @Param("provider") Provider provider,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    // --- Rate-limit queries ---

    long countByProviderAndResponseStatusAndCreatedAtBetween(
        Provider provider, int responseStatus, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(l.tokensInput), 0) FROM ApiUsageLog l " +
           "WHERE l.provider = :provider AND l.responseStatus = :responseStatus " +
           "AND l.createdAt BETWEEN :from AND :to AND l.tokensInput IS NOT NULL")
    Long sumTokensInputByProviderAndResponseStatusAndCreatedAtBetween(
        @Param("provider") Provider provider,
        @Param("responseStatus") int responseStatus,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    // --- Per-model queries ---

    long countByProviderAndModelNameAndCreatedAtBetween(
        Provider provider, String modelName, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(l.tokensInput), 0) FROM ApiUsageLog l " +
           "WHERE l.provider = :provider AND l.modelName = :modelName " +
           "AND l.createdAt BETWEEN :from AND :to AND l.tokensInput IS NOT NULL AND l.responseStatus = 200")
    Long sumTokensInputByProviderAndModelNameAndCreatedAtBetween(
        @Param("provider") Provider provider,
        @Param("modelName") String modelName,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(l.tokensOutput), 0) FROM ApiUsageLog l " +
           "WHERE l.provider = :provider AND l.modelName = :modelName " +
           "AND l.createdAt BETWEEN :from AND :to AND l.tokensOutput IS NOT NULL AND l.responseStatus = 200")
    Long sumTokensOutputByProviderAndModelNameAndCreatedAtBetween(
        @Param("provider") Provider provider,
        @Param("modelName") String modelName,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);
}
