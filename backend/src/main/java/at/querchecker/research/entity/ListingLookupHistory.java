package at.querchecker.research.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "listing_lookup_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingLookupHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long listingId;

    @Column(nullable = false, length = 500)
    private String lookupTerm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LookupStatus lookupStatus;

    @Column(columnDefinition = "TEXT")
    private String quickFactsJson;

    private String icecatId;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private String sourceDomain;

    private String siteLabel;

    @Column(length = 1000)
    private String sourceUrl;

    @Column(columnDefinition = "TEXT")
    private String featureGroupsJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
