package at.querchecker.sse;

import at.querchecker.dto.DlExtractionDonePayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseHub sseHub;

    /**
     * Opens a persistent SSE connection for the given client instance.
     * Events emitted on this stream use the payload types documented below.
     */
    @Operation(summary = "Open SSE event stream")
    @ApiResponse(responseCode = "200", description = "SSE stream — event payloads",
        content = @Content(schema = @Schema(oneOf = {DlExtractionDonePayload.class})))
    @GetMapping(produces = "text/event-stream")
    public SseEmitter connect(@RequestParam String eventSourceId) {
        return sseHub.register(eventSourceId);
    }
}
