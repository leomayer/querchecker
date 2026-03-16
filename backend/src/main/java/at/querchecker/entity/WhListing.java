package at.querchecker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "wh_listing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String whId;

    // nullable – Willhaben liefert HEADING nicht immer
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private BigDecimal price;

    private String location;

    /** Relativer SEO-Pfad (ohne https://www.willhaben.at/iad/ Prefix). */
    @Column(columnDefinition = "TEXT")
    private String url;

    private LocalDateTime listedAt;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    /** Relativer Bildpfad-Stem (ohne Prefix und ohne _thumb.jpg Suffix). */
    @Column(columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(columnDefinition = "boolean not null default false")
    private boolean paylivery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wh_category_id")
    private WhCategory whCategory;

    /** Alle Bildpfad-Stems (ohne Prefix und ohne .jpg Suffix). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "wh_listing_image", joinColumns = @JoinColumn(name = "listing_id"))
    @Column(name = "image_path", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> imagePaths = new ArrayList<>();
}
