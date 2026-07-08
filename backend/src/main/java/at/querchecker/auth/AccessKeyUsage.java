package at.querchecker.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Ebene-2-Kontingent: zählt Nutzeraktionen (Spec-Lookups) pro Key und Tag.
 * History-Tabelle — eine Zeile je (accessKeyId, periodDate), nie überschrieben
 * außer durch das atomare Increment-Upsert (siehe {@link AccessKeyUsageRepository}).
 * Konzept: berechtigungen-konzept.md Kap. 4.
 */
@Entity
@Table(name = "access_key_usage")
@Getter
@Setter
@NoArgsConstructor
public class AccessKeyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_key_id", nullable = false)
    private Long accessKeyId;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(name = "consumed_count", nullable = false)
    private int consumedCount;

    /** Ebene-2b: DL-Extraktion (Inseratanalyse) — separater, generöser Hintergrund-Zähler. */
    @Column(name = "extraction_consumed_count", nullable = false)
    private int extractionConsumedCount;
}
