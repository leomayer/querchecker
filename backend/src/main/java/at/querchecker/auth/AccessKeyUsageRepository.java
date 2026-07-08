package at.querchecker.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface AccessKeyUsageRepository extends JpaRepository<AccessKeyUsage, Long> {

    /**
     * Verbleibendes Tageskontingent des Keys in einer Query — die DB rechnet,
     * nicht Java (Konzept Kap. 4). {@code null}, wenn der Key nicht existiert.
     */
    @Query(value = """
        SELECT k.quota_limit - COALESCE(u.consumed_count, 0)
        FROM access_key k
        LEFT JOIN access_key_usage u
               ON u.access_key_id = k.id AND u.period_date = CURRENT_DATE
        WHERE k.id = :accessKeyId
        """, nativeQuery = true)
    Integer findRemainingToday(@Param("accessKeyId") Long accessKeyId);

    /**
     * Atomares Upsert — kein Read-Modify-Write in Java, race-condition-sicher
     * bei parallelen Requests desselben Keys.
     */
    @Modifying
    @Query(value = """
        INSERT INTO access_key_usage (access_key_id, period_date, consumed_count)
        VALUES (:accessKeyId, CURRENT_DATE, 1)
        ON CONFLICT (access_key_id, period_date)
        DO UPDATE SET consumed_count = access_key_usage.consumed_count + 1
        """, nativeQuery = true)
    void incrementToday(@Param("accessKeyId") Long accessKeyId);

    /**
     * Verbleibendes Tages-Hintergrundkontingent für DL-Extraktion (Ebene-2b, Konzept Kap. 4) —
     * generöser Multiplikator des Lookup-Kontingents, da automatisch pro Detailansicht ausgelöst.
     */
    @Query(value = """
        SELECT (k.quota_limit * :multiplier) - COALESCE(u.extraction_consumed_count, 0)
        FROM access_key k
        LEFT JOIN access_key_usage u
               ON u.access_key_id = k.id AND u.period_date = CURRENT_DATE
        WHERE k.id = :accessKeyId
        """, nativeQuery = true)
    Integer findExtractionRemainingToday(@Param("accessKeyId") Long accessKeyId, @Param("multiplier") int multiplier);

    @Modifying
    @Query(value = """
        INSERT INTO access_key_usage (access_key_id, period_date, extraction_consumed_count)
        VALUES (:accessKeyId, CURRENT_DATE, 1)
        ON CONFLICT (access_key_id, period_date)
        DO UPDATE SET extraction_consumed_count = access_key_usage.extraction_consumed_count + 1
        """, nativeQuery = true)
    void incrementExtractionToday(@Param("accessKeyId") Long accessKeyId);

    void deleteByPeriodDateBefore(LocalDate cutoff);
}
