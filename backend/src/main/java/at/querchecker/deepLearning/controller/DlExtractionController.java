package at.querchecker.deepLearning.controller;

import at.querchecker.deepLearning.DlExtractionCompletedEvent;
import at.querchecker.deepLearning.repository.DlExtractionTermRepository;
import at.querchecker.dto.DlExtractionDonePayload;
import at.querchecker.dto.DlExtractionTermDto;
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
    private final SseHub sseHub;

    /**
     * Returns all extraction terms for an itemTextId, sorted by confidence descending.
     */
    @GetMapping("/extraction/{itemTextId}/terms")
    public List<DlExtractionTermDto> getTerms(@PathVariable Long itemTextId) {
        return termRepo.findByItemTextId(itemTextId).stream()
            .map(t -> DlExtractionTermDto.builder()
                .modelName(t.getRun().getModelConfig().getModelName())
                .term(t.getTerm())
                .confidence(t.getConfidence())
                .durationMs(t.getRun().getDurationMs())
                .build())
            .toList();
    }

    @EventListener
    public void onExtractionCompleted(DlExtractionCompletedEvent event) {
        try {
            List<DlExtractionTermDto> terms = termRepo
                .findByItemTextIdAndModelName(event.getItemTextId(), event.getModelName()).stream()
                .map(t -> DlExtractionTermDto.builder()
                    .modelName(t.getRun().getModelConfig().getModelName())
                    .term(t.getTerm())
                    .confidence(t.getConfidence())
                    .durationMs(t.getRun().getDurationMs())
                    .build())
                .toList();

            log.debug("Broadcasting dl-extract: itemTextId={}, model={}, terms={}",
                event.getItemTextId(), event.getModelName(), terms.size());

            sseHub.broadcast("dl-extract", DlExtractionDonePayload.builder()
                .itemTextId(event.getItemTextId())
                .terms(terms)
                .build());
        } catch (Exception e) {
            log.error("Failed to broadcast dl-extract for itemTextId={}, model={}",
                event.getItemTextId(), event.getModelName(), e);
        }
    }
}
