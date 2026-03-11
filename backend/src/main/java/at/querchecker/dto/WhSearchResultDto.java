package at.querchecker.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhSearchResultDto {
    /** Gesamtanzahl der Treffer laut Willhaben (kann größer sein als die abgerufene Seitenanzahl). */
    private Integer totalCount;
    private List<WhItemDto> listings;
}
