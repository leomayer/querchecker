package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DlExtractionDonePayload {
    private Long itemTextId;
}
