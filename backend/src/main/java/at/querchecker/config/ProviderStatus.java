package at.querchecker.config;

/**
 * SSE-Payload und REST-Antwort für den aktuellen Provider-Status.
 * Wird beim SSE-Connect und bei jeder Statusänderung gesendet.
 */
public record ProviderStatus(
    ProviderState searchState,
    ProviderState llmState,
    String searchProvider,       // "BRAVE" | "GOOGLE_DISCOVERY" | null
    String llmProvider,          // "GROQ" | "OPENROUTER" | "LOCAL" | null
    String searchError,          // Fehlergrund — nur bei UNREACHABLE / UNAVAILABLE
    String llmError,
    Integer searchHttpStatus,    // HTTP-Status — nur bei UNREACHABLE / UNAVAILABLE
    Integer llmHttpStatus,
    String serverStartToken      // = SseHub.startToken (für hash-basiertes Popup-Acknowledgement)
) {}
