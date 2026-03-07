package at.querchecker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "wh_listing_detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhListingDetail {

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

    /** null = kein Rating, "UP" = interessant, "DOWN" = nicht interessant */
    private String rating;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
