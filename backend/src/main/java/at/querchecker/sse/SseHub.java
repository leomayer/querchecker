package at.querchecker.sse;

import lombok.extern.slf4j.Slf4j;
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

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String eventSourceId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min
        emitters.put(eventSourceId, emitter);
        emitter.onCompletion(() -> emitters.remove(eventSourceId));
        emitter.onTimeout(() -> emitters.remove(eventSourceId));
        emitter.onError(e -> emitters.remove(eventSourceId));
        log.debug("SSE client registered: {}, active={}", eventSourceId, emitters.size());
        return emitter;
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
            }
        });
    }
}
