package at.querchecker.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SessionCookieAuthFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "qc_session";

    private final UserSessionRepository userSessionRepository;
    private final AccessKeyRepository accessKeyRepository;
    private final AuthProperties authProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Fehlt der Cookie, ist ungültig/abgelaufen oder der Key revoked -> Kette läuft als GUEST weiter,
        // kein 401 an dieser Stelle (greift erst über SecurityConfig.authorizeHttpRequests).
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            readCookie(request).flatMap(this::resolvePrincipal)
                .ifPresent(principal -> {
                    var auth = new PreAuthenticatedAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                    auth.setAuthenticated(true);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
        }
        chain.doFilter(request, response);
    }

    private Optional<QuerCheckerPrincipal> resolvePrincipal(String rawToken) {
        return userSessionRepository.findByTokenHash(DigestUtils.sha256Hex(rawToken))
            .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
            .flatMap(session -> accessKeyRepository.findById(session.getAccessKeyId())
                .filter(key -> !key.isRevoked())
                .map(key -> {
                    extendIfNeeded(session);
                    return QuerCheckerPrincipal.withKey(key.getRole(), key.getId());
                }));
    }

    // Max. eine Verlängerung pro sliding-extension-hours: nur schreiben, wenn die Restlaufzeit
    // bereits unter (session-days - sliding-extension-hours) gefallen ist.
    private void extendIfNeeded(UserSession session) {
        Instant now = Instant.now();
        Duration sessionLength = Duration.ofDays(authProperties.getSessionDays());
        Duration slidingExtension = Duration.ofHours(authProperties.getSlidingExtensionHours());
        if (Duration.between(now, session.getExpiresAt()).compareTo(sessionLength.minus(slidingExtension)) < 0) {
            session.setExpiresAt(now.plus(sessionLength));
            userSessionRepository.save(session);
        }
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
            .filter(c -> COOKIE_NAME.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst();
    }
}
