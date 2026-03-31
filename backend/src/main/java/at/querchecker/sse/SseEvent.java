package at.querchecker.sse;

/**
 * Unified SSE event envelope for all event types.
 * Allows frontend to dispatch events consistently by eventType.
 */
public record SseEvent<T>(
    String eventType,        // "dl-extract", "lookup-result", "error-notification", "listing-refreshed"
    Long whListingId,        // Correlation key across all event types
    T payload,
    Long timestamp           // Event timestamp (ms since epoch)
) {
    public SseEvent(String eventType, Long whListingId, T payload) {
        this(eventType, whListingId, payload, System.currentTimeMillis());
    }
}
