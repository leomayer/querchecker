package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DlExtractionDonePayload {
    private Long whItemId;
    private List<DlExtractionTermDto> terms;
}
