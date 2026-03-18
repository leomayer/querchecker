package at.querchecker.deepLearning.controller;

import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.entity.DlExtractionTerm;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import at.querchecker.dto.DlExtractionDonePayload;
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
    private final WhItemRepository whItemRepository;
    private final SseHub sseHub;

    /**
     * Returns all extraction terms for a whItemId, sorted by confidence descending.
     */
    @GetMapping("/extraction/{whItemId}/terms")
    public List<DlExtractionTermDto> getTerms(@PathVariable Long whItemId) {
        return toDto(termRepo.findByWhItemId(whItemId));
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
