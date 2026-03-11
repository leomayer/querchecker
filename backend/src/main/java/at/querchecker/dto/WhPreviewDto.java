package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhPreviewDto {
    private String thumbUrl;
    private String fullUrl;
}
