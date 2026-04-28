package at.querchecker.config;

import at.querchecker.api.config.LlmProperties;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.search.SearchProperties;
import at.querchecker.sse.SseHub;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Verwaltet den Laufzeit-Status aller externen Provider (Web Search + LLM).
 *
 * Beim Start: statische Konfig-Prüfung → UNCONFIGURED oder CONFIGURED.
 * Lazy (erster echter Call): VALID | UNREACHABLE | UNAVAILABLE via markValid/markUnreachable/markUnavailable.
 * Bei Statusänderung: SSE-Event "provider-status" an alle verbundenen Clients.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderStatusService {

    // Platzhalter-Keys — Backend-kanonisch, identisch in example.yml
    public static final String BRAVE_PLACEHOLDER         = "BRAVE_PLACEHOLDER";
    public static final String GROQ_PLACEHOLDER          = "GROQ_PLACEHOLDER";
    public static final String OPENROUTER_PLACEHOLDER    = "OPENROUTER_PLACEHOLDER";
    public static final String GOOGLE_CREDENTIALS_PLACEHOLDER = "GOOGLE_CREDENTIALS_PLACEHOLDER";

    private final ProviderProperties providerProperties;
    private final SearchProperties searchProperties;
    private final LlmProperties llmProperties;
    private final SseHub sseHub;

    private final AtomicReference<ProviderState> searchState  = new AtomicReference<>(ProviderState.UNCONFIGURED);
    private final AtomicReference<ProviderState> llmState     = new AtomicReference<>(ProviderState.UNCONFIGURED);
    private final AtomicReference<String>        searchError  = new AtomicReference<>(null);
    private final AtomicReference<String>        llmError     = new AtomicReference<>(null);
    private final AtomicReference<Integer>       searchHttp   = new AtomicReference<>(null);
    private final AtomicReference<Integer>       llmHttp      = new AtomicReference<>(null);

    // --- Startup ---

    @PostConstruct
    public void init() {
        searchState.set(checkSearchProvider());
        llmState.set(checkLlmProvider());
        log.info("[ProviderStatus] Startup check — search={} ({}), llm={} ({})",
            searchState.get(), searchProperties.getActiveProvider(),
            llmState.get(), llmProperties.getMode());
    }

    // --- Static config checks ---

    private ProviderState checkSearchProvider() {
        return switch (searchProperties.getActiveProvider()) {
            case BRAVE -> {
                String key = providerProperties.getProvider(
                    at.querchecker.api.entity.Provider.BRAVE).getApiKey();
                yield isUnconfigured(key, BRAVE_PLACEHOLDER)
                    ? ProviderState.UNCONFIGURED : ProviderState.CONFIGURED;
            }
            case GOOGLE_DISCOVERY -> {
                String path = providerProperties.getGoogleDiscovery() != null
                    ? providerProperties.getGoogleDiscovery().getCredentialsPath() : null;
                yield isUnconfigured(path, GOOGLE_CREDENTIALS_PLACEHOLDER)
                    ? ProviderState.UNCONFIGURED : ProviderState.CONFIGURED;
            }
        };
    }

    private ProviderState checkLlmProvider() {
        if (llmProperties.getMode() == at.querchecker.api.config.LlmMode.LOCAL) {
            return ProviderState.CONFIGURED; // LOCAL-Modelle: DJL wirft beim Start → UNAVAILABLE
        }
        return switch (llmProperties.getActiveProvider()) {
            case GROQ -> {
                String key = providerProperties.getProvider(
                    at.querchecker.api.entity.Provider.GROQ).getApiKey();
                yield isUnconfigured(key, GROQ_PLACEHOLDER)
                    ? ProviderState.UNCONFIGURED : ProviderState.CONFIGURED;
            }
            case OPENROUTER -> {
                String key = providerProperties.getProvider(
                    at.querchecker.api.entity.Provider.OPENROUTER).getApiKey();
                yield isUnconfigured(key, OPENROUTER_PLACEHOLDER)
                    ? ProviderState.UNCONFIGURED : ProviderState.CONFIGURED;
            }
            default -> ProviderState.UNCONFIGURED;
        };
    }

    /**
     * Ein Provider gilt als UNCONFIGURED wenn:
     * - key ist null, leer oder nur Whitespace
     * - key entspricht dem kanonischen Platzhalter-Key (exakter Vergleich)
     */
    public static boolean isUnconfigured(String key, String placeholder) {
        return key == null || key.isBlank() || key.equals(placeholder);
    }

    // --- Runtime state transitions ---

    public void markValid(boolean isSearch) {
        AtomicReference<ProviderState> state = isSearch ? searchState : llmState;
        ProviderState prev = state.getAndSet(ProviderState.VALID);
        if (prev != ProviderState.VALID) {
            if (isSearch) {
                searchError.set(null);
                searchHttp.set(null);
            } else {
                llmError.set(null);
                llmHttp.set(null);
            }
            log.info("[ProviderStatus] {} → VALID (was {})", isSearch ? "search" : "llm", prev);
            broadcastStatus();
        }
    }

    public void markUnreachable(boolean isSearch, String reason, int httpStatus) {
        AtomicReference<ProviderState> state = isSearch ? searchState : llmState;
        ProviderState prev = state.getAndSet(ProviderState.UNREACHABLE);
        if (isSearch) { searchError.set(reason); searchHttp.set(httpStatus); }
        else           { llmError.set(reason);    llmHttp.set(httpStatus); }
        if (prev != ProviderState.UNREACHABLE) {
            log.warn("[ProviderStatus] {} → UNREACHABLE (was {}, status={}, reason={})",
                isSearch ? "search" : "llm", prev, httpStatus, reason);
            broadcastStatus();
        }
    }

    public void markUnavailable(boolean isSearch, String reason, int httpStatus) {
        AtomicReference<ProviderState> state = isSearch ? searchState : llmState;
        ProviderState prev = state.getAndSet(ProviderState.UNAVAILABLE);
        if (isSearch) { searchError.set(reason); searchHttp.set(httpStatus); }
        else           { llmError.set(reason);    llmHttp.set(httpStatus); }
        if (prev != ProviderState.UNAVAILABLE) {
            log.warn("[ProviderStatus] {} → UNAVAILABLE (was {}, status={}, reason={})",
                isSearch ? "search" : "llm", prev, httpStatus, reason);
            broadcastStatus();
        }
    }

    // --- Status snapshot ---

    public ProviderStatus getStatus() {
        return new ProviderStatus(
            searchState.get(),
            llmState.get(),
            searchProperties.getActiveProvider().name(),
            resolveLlmProviderName(),
            searchError.get(),
            llmError.get(),
            searchHttp.get(),
            llmHttp.get(),
            sseHub.getStartToken()
        );
    }

    // --- SSE broadcast ---

    /** Wird von SseHub beim Connect aufgerufen — sendet initialen Status. */
    public void broadcastStatus() {
        sseHub.broadcast("provider-status", getStatus());
    }

    // --- helpers ---

    private String resolveLlmProviderName() {
        if (llmProperties.getMode() == at.querchecker.api.config.LlmMode.LOCAL) return "LOCAL";
        return llmProperties.getActiveProvider().name();
    }
}
