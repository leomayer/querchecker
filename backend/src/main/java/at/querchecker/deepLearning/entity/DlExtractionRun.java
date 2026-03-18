package at.querchecker.deepLearning.entity;

import at.querchecker.deepLearning.ExtractionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.LocalDateTime;

@Entity
@Table(name = "dl_extraction_run")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlExtractionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_text_id", nullable = false)
    private ItemText itemText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_config_id", nullable = false)
    private DlModelConfig modelConfig;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false, length = 64)
    private String inputHash;

    private LocalDateTime extractedAt;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "extraction_status")
    private ExtractionStatus status;

    @Column(length = 500)
    private String errorMessage;

    private Long durationMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = ExtractionStatus.INIT;
        }
    }
}
