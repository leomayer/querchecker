package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DlExtractionStatusResponse {

    /**
     * Overall extraction status for this item.
     * "DONE"    — at least one model finished successfully
     * "PENDING" — extraction is running or freshly scheduled
     * "CANCELLED" — all scheduled runs were cancelled (queue overflow); retry triggered automatically
     * "NONE"    — no extraction runs exist yet
     */
    private String extractionStatus;

    private List<DlExtractionTermDto> terms;
}
