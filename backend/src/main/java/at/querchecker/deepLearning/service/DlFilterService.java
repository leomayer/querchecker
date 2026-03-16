package at.querchecker.deepLearning.service;

import at.querchecker.deepLearning.ExtractionResult;
import at.querchecker.deepLearning.config.DlConfig;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class DlFilterService {

    public List<ExtractionResult> filter(List<ExtractionResult> raw, DlConfig config) {
        return raw.stream()
            .sorted(Comparator.comparingDouble(ExtractionResult::getConfidence).reversed())
            .limit(config.getTopK())
            .filter(r -> r.getConfidence() >= config.getMinConfidence())
            .toList();
    }
}
