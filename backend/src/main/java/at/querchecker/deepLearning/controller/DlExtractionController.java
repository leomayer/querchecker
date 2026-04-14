package at.querchecker.deepLearning.controller;

import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.ExtractionStatus;
import at.querchecker.deepLearning.entity.DlExtractionTerm;
import at.querchecker.deepLearning.repository.DlExtractionRunRepository;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import at.querchecker.deepLearning.repository.ItemTextRepository;
import at.querchecker.deepLearning.service.DlOrchestrationService;
import at.querchecker.dto.DlExtractionDonePayload;
import at.querchecker.dto.DlExtractionStatusResponse;
import at.querchecker.dto.DlExtractionTermDto;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.api.config.LlmProperties;
import at.querchecker.sse.SseHub;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/dl")
@RequiredArgsConstructor
public class DlExtractionController {

    private final DlExtractionTermRepository termRepo;
    private final DlExtractionRunRepository runRepo;
    private final ItemTextRepository itemTextRepository;
    private final WhItemRepository whItemRepository;
    private final DlOrchestrationService dlOrchestrationService;
    private final SseHub sseHub;
    private final LlmProperties llmProperties;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Returns extraction terms + overall status for a whItemId.
     * Auto-retries if the only existing runs are CANCELLED (queue overflow scenario).
     */
    @GetMapping("/extraction/{whItemId}/terms")
    public DlExtractionStatusResponse getTerms(@PathVariable Long whItemId) {
        List<DlExtractionTermDto> terms = toDto(termRepo.findByWhItemId(whItemId));
        List<ExtractionStatus> runStatuses = runRepo.findStatusesByWhItemId(whItemId);
        String extractionStatus = deriveStatus(runStatuses, terms);

        if ("CANCELLED".equals(extractionStatus)) {
            log.debug("Auto-retrying CANCELLED extraction for whItemId={}", whItemId);
            itemTextRepository.findByWhItemIdOrderByFetchedAtDesc(whItemId).stream()
                .findFirst()
                .ifPresent(dlOrchestrationService::scheduleExtraction);
            extractionStatus = "PENDING";
        }

        return DlExtractionStatusResponse.builder()
            .extractionStatus(extractionStatus)
            .terms(terms)
            .suggestedTerm(deriveSuggestedTerm(terms))
            .build();
    }

    @EventListener
    public void onExtractionCompleted(DlExtractionCompletedEvent event) {
        try {
            Long whItemId = whItemRepository.findIdByItemTextId(event.getItemTextId())
                .orElse(null);
            if (whItemId == null) {
                log.warn("No WhItem found for itemTextId={}, skipping SSE broadcast", event.getItemTextId());
                return;
            }

            List<DlExtractionTermDto> terms = toDto(
                termRepo.findByItemTextIdAndModelName(event.getItemTextId(), event.getModelName()));

            // Derive suggestedTerm from ALL terms for this item (not just this model's)
            List<DlExtractionTermDto> allTerms = toDto(termRepo.findByWhItemId(whItemId));
            String suggestedTerm = deriveSuggestedTerm(allTerms);

            log.debug("Broadcasting dl-extract: whItemId={}, model={}, terms={}, suggested={}, modelStatus={}",
                whItemId, event.getModelName(), terms.size(), suggestedTerm, event.getModelStatus());

            sseHub.broadcast("dl-extract", DlExtractionDonePayload.builder()
                .whItemId(whItemId)
                .terms(terms)
                .suggestedTerm(suggestedTerm)
                .modelStatus(event.getModelStatus().name())
                .build());
        } catch (Exception e) {
            log.error("Failed to broadcast dl-extract for itemTextId={}, model={}",
                event.getItemTextId(), event.getModelName(), e);
        }
    }

    private String deriveStatus(List<ExtractionStatus> statuses, List<DlExtractionTermDto> terms) {
        if (!terms.isEmpty()) return "DONE";
        if (statuses.isEmpty()) return "NONE";
        if (statuses.stream().anyMatch(s -> s == ExtractionStatus.DONE)) return "DONE";
        if (statuses.stream().anyMatch(s -> s == ExtractionStatus.INIT || s == ExtractionStatus.PENDING)) return "PENDING";
        if (statuses.stream().anyMatch(s -> s == ExtractionStatus.CANCELLED)) return "CANCELLED";
        if (statuses.stream().allMatch(s -> s == ExtractionStatus.FAILED)) return "FAILED";
        return "NONE";
    }

    private String deriveSuggestedTerm(List<DlExtractionTermDto> terms) {
        String sourceModel = llmProperties.getEffectiveSourceModel();
        return terms.stream()
            .filter(t -> t.getModelName() != null && t.getModelName().toLowerCase().contains(sourceModel))
            .max(Comparator.comparingDouble(t -> t.getConfidence() != null ? t.getConfidence().doubleValue() : 0.0))
            .map(DlExtractionTermDto::getTerm)
            .orElse(null);
    }

    private List<DlExtractionTermDto> toDto(List<DlExtractionTerm> terms) {
        return terms.stream()
            .map(t -> DlExtractionTermDto.builder()
                .modelName(t.getRun().getModelConfig().getModelName())
                .term(t.getTerm())
                .confidence(t.getConfidence())
                .durationMs(t.getRun().getDurationMs())
                .condensedSpec(parseCondensedSpec(t.getCondensedSpecsJson()))
                .extractedModel(t.getExtractedModel())
                .build())
            .toList();
    }

    private Map<String, String> parseCondensedSpec(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, String> rawMap = MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});

            return rawMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (Exception e) {
            log.warn("Failed to parse condensedSpecsJson: {}", e.getMessage());
            return null;
        }
    }
}
