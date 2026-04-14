package at.querchecker.deepLearning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionResult {
    private String term;
    private double confidence;
    private Map<String, String> condensedSpec;
    private String extractedModel;
}
