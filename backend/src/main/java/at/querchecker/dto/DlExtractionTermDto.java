package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class DlExtractionTermDto {
    private String modelName;
    private String term;
    private Float confidence;
    private Long durationMs;
    private Map<String, String> condensedSpec;
    private String extractedModel;
}
