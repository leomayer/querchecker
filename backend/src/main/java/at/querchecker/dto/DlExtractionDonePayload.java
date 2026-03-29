package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DlExtractionDonePayload {
    private Long whItemId;
    private List<DlExtractionTermDto> terms;
    /** Best term from the configured source model — pre-fills the spec-lookup field. */
    private String suggestedTerm;
    /** Extraction status of this model's run: "DONE" or "FAILED". */
    private String modelStatus;
}
