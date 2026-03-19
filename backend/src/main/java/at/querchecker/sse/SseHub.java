package at.querchecker.sse;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active SSE client connections.
 * Each Angular app instance connects once on startup with a unique eventSourceId.
 * Any server-side event can broadcast to all connected clients.
 */
@Slf4j
@Service
public class SseHub {

    /** Startup timestamp (ms since epoch) — re-evaluated on every bean creation (context restart). */
    private final String startToken = String.valueOf(System.currentTimeMillis());

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String eventSourceId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout — client reconnects on its own
        emitters.put(eventSourceId, emitter);
        emitter.onCompletion(() -> emitters.remove(eventSourceId, emitter));
        emitter.onTimeout(() -> emitters.remove(eventSourceId, emitter));
        emitter.onError(e -> emitters.remove(eventSourceId, emitter));
        log.debug("SSE client registered: {}, active={}", eventSourceId, emitters.size());
        try {
            emitter.send(SseEmitter.event()
                .name("server-hello")
                .data(startToken));
        } catch (IOException e) {
            log.debug("Failed to send server-hello to {}", eventSourceId);
        }
        return emitter;
    }

    /** Broadcasts the server token every 20 s so Angular can detect stale connections. */
    @Scheduled(fixedDelay = 20_000)
    public void sendKeepalive() {
        if (!emitters.isEmpty()) {
            broadcast("keepalive", startToken);
        }
    }

    /**
     * Completes all active emitters on context shutdown so browsers detect the disconnect
     * and automatically reconnect, which triggers a new server-hello with the fresh token.
     */
    @PreDestroy
    public void destroy() {
        log.debug("SseHub shutting down — completing {} emitter(s)", emitters.size());
        emitters.forEach((id, emitter) -> {
            try { emitter.complete(); } catch (Exception ignored) {}
        });
        emitters.clear();
    }

    /**
     * Broadcasts a named event with a JSON-serializable payload to all connected clients.
     */
    public void broadcast(String eventName, Object data) {
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.debug("SSE send failed for client {}, removing", id);
                emitters.remove(id);
                emitter.complete();
            }
        });
    }
}
