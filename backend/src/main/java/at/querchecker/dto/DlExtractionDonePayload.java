package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DlExtractionDonePayload {
    private Long itemTextId;
    private List<DlExtractionTermDto> terms;
}
