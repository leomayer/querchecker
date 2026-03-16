package at.querchecker.deepLearning.entity;

import at.querchecker.deepLearning.ItemSource;
import at.querchecker.entity.WhListing;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "item_text")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wh_listing_id")
    private WhListing whListing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemSource source;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;
}
