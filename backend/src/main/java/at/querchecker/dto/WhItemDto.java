package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder(toBuilder = true)
public class WhItemDto {

    private Long id;
    private String whId;
    private String title;
    private String description;
    private BigDecimal price;
    private String location;
    /** Vollständige Willhaben-URL (Prefix + SEO-Pfad). */
    private String url;
    private LocalDateTime listedAt;
    private LocalDateTime fetchedAt;
    /** Vollständige Thumbnail-URL (Prefix + Pfad + _thumb.jpg). */
    private String thumbnailUrl;
    /** Alle Bilder als vollständige URLs (Prefix + Pfad + .jpg). */
    private List<String> imageUrls;

    // Aus wh_item – benutzereigene Daten
    private boolean hasNote;
    private int viewCount;
    private LocalDateTime lastViewedAt;
    /** null = kein Rating, "UP" = interessant, "DOWN" = nicht interessant */
    private String rating;
    /** null = kein Level, "LOW" / "MEDIUM" / "HIGH" */
    private String interestLevel;
    private boolean paylivery;
    private List<WhCategoryDto> categoryPath;
}
