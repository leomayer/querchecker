package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** SSE payload broadcast after an async Willhaben detail refresh. */
@Data
@Builder
public class ListingRefreshedPayload {
    private Long whItemId;
    private String description;
    private List<WhPreviewDto> previews;
    private List<WhCategoryDto> categoryPath;
}
