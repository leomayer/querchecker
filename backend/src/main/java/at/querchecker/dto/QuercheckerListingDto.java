package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
public class QuercheckerListingDto {

    private Long id;
    private String whId;
    private String title;
    private String description;
    private BigDecimal price;
    private String location;
    private String url;
    private LocalDateTime listedAt;
    private LocalDateTime fetchedAt;
    /** Thumbnail-URL aus dem Live-API-Response – nicht in der DB gespeichert. */
    private String thumbnailUrl;
}
