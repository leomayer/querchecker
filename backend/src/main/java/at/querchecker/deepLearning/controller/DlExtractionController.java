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
import at.querchecker.sse.SseHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

            log.debug("Broadcasting dl-extract: whItemId={}, model={}, terms={}",
                whItemId, event.getModelName(), terms.size());

            sseHub.broadcast("dl-extract", DlExtractionDonePayload.builder()
                .whItemId(whItemId)
                .terms(terms)
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
        return "NONE";
    }

    private List<DlExtractionTermDto> toDto(List<DlExtractionTerm> terms) {
        return terms.stream()
            .map(t -> DlExtractionTermDto.builder()
                .modelName(t.getRun().getModelConfig().getModelName())
                .term(t.getTerm())
                .confidence(t.getConfidence())
                .durationMs(t.getRun().getDurationMs())
                .build())
            .toList();
    }
}
