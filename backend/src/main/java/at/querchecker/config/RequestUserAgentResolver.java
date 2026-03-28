package at.querchecker.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Löst den User-Agent für ausgehende HTTP-Requests auf.
 * Bevorzugt den vom Browser weitergeleiteten UA (X-Querchecker-User-Agent),
 * fällt sonst auf den gecachten UA aus UserAgentHolder zurück.
 */
@Component
@RequiredArgsConstructor
public class RequestUserAgentResolver {

    static final String HEADER = "X-Querchecker-User-Agent";

    private final UserAgentHolder userAgentHolder;

    public String resolve() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String forwarded = attrs.getRequest().getHeader(HEADER);
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded;
                }
            }
        } catch (Exception ignored) {
        }
        return userAgentHolder.get();
    }
}
