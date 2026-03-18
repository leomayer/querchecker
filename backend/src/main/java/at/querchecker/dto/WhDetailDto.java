package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vollständiges DTO für die Detail-Ansicht eines Inserats.
 * Kombiniert Listing-Metadaten (WhListing), Live-Inhalte (WH-API) und
 * Benutzer-Annotationen (WhItem) in einem einzigen Objekt.
 */
@Data
@Builder
public class WhDetailDto {

    // --- DL-Extraktion ---
    /** ID des WhItem-Records – für SSE-Stream und Term-Abruf. */
    private Long whItemId;

    // --- Listing-Metadaten (aus WhListing) ---
    private Long id;
    private String whId;
    private String title;
    private BigDecimal price;
    private String location;
    private String url;
    private LocalDateTime listedAt;
    private LocalDateTime fetchedAt;
    private boolean paylivery;
    private List<WhCategoryDto> categoryPath;

    // --- Live-Inhalte (von WH-API abgerufen) ---
    /** Vollständige Beschreibung (live von WH geholt). */
    private String description;
    /** Vorschaubilder (live von WH geholt oder aus DB-Pfaden gebaut). */
    private List<WhPreviewDto> previews;

    // --- Benutzer-Annotationen (aus WhItem) ---
    private String note;
    private String rating;
    private String interestLevel;
    private List<String> tags;
    private int viewCount;
    private LocalDateTime lastViewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
