package at.querchecker.sse;

/**
 * SSE payload for error notifications.
 * Covers DL extraction failures, spec-lookup failures, network errors, etc.
 */
public record ErrorNotificationPayload(
    String errorType,           // "NETWORK_ERROR", "API_TIMEOUT", "EXTRACTION_FAILED", "LOOKUP_FAILED"
    String message,             // User-facing message (German)
    Long retryAfterSeconds      // Optional: if retryable, seconds to wait before retry
) {}
