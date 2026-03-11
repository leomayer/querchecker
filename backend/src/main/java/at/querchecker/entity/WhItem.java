package at.querchecker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    /** null = kein Rating, "UP" = interessant, "DOWN" = nicht interessant */
    private String rating;

    /** null = kein Level, "LOW" / "MEDIUM" / "HIGH" */
    private String interestLevel;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wh_item_tag", joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
