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

    /**
     * Best term from the configured source model (querchecker.llm.local-source-model / active-provider),
     * selected by highest confidence. Null if no matching model terms exist yet.
     * Used to pre-fill the product search field in the research panel.
     */
    private String suggestedTerm;
}
