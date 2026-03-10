package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class WhListingDetailDto {

    private Long id;
    private Long whListingId;
    private String note;
    private int viewCount;
    private LocalDateTime lastViewedAt;
    private String rating;
    private String interestLevel;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
