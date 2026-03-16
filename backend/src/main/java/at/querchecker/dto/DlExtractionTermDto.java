package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DlExtractionTermDto {
    private String modelName;
    private String term;
    private Float confidence;
}
