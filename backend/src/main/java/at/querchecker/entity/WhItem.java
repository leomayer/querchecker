package at.querchecker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "wh_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wh_listing_id", nullable = false, unique = true)
    private WhListing whListing;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    @Builder.Default
    private int viewCount = 0;

    private LocalDateTime lastViewedAt;

    /** Vom User bestätigter/korrigierter Suchterm für den Spec-Lookup */
    private String lookupTerm;

    /** null = kein Rating, "UP" = interessant, "DOWN" = nicht interessant */
    private String rating;

    /** null = kein Level, "LOW" / "MEDIUM" / "HIGH" */
    private String interestLevel;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
