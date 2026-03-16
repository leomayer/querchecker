package at.querchecker.deepLearning.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dl_extraction_term")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlExtractionTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private DlExtractionRun run;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String term;

    private Float confidence;

    private String userCorrectedTerm;

    private LocalDateTime userCorrectedAt;

    private String correctionNote;
}
