package at.querchecker.auth;

import at.querchecker.auth.dto.AuthStatusDto;
import at.querchecker.auth.dto.LoginResponseDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String COOKIE_NAME = "qc_session";

    private final AccessKeyRepository accessKeyRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuthProperties authProperties;
    private final AccessKeyUsageService accessKeyUsageService;

    public LoginResponseDto login(String submittedKey, HttpServletResponse response) {
        AccessKey accessKey = accessKeyRepository.findBySecretKeyHash(DigestUtils.sha256Hex(submittedKey))
            .filter(k -> !k.isRevoked())
            // Gleiche Fehlermeldung für unbekannten und gesperrten Key — kein Information-Leak.
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ungültiger oder gesperrter Zugriffscode"));

        accessKey.setUsed(true);
        accessKey.setLastUsedAt(Instant.now());
        accessKeyRepository.save(accessKey);

        String rawToken = UUID.randomUUID().toString();
        UserSession session = new UserSession();
        session.setTokenHash(DigestUtils.sha256Hex(rawToken));
        session.setAccessKeyId(accessKey.getId());
        session.setExpiresAt(Instant.now().plus(Duration.ofDays(authProperties.getSessionDays())));
        userSessionRepository.save(session);

        setCookie(response, rawToken, Duration.ofDays(authProperties.getSessionDays()));

        return new LoginResponseDto(accessKey.getRole());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        readCookie(request).ifPresent(rawToken ->
            userSessionRepository.findByTokenHash(DigestUtils.sha256Hex(rawToken))
                .ifPresent(userSessionRepository::delete));
        setCookie(response, "", Duration.ZERO);
    }

    public AuthStatusDto me() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof QuerCheckerPrincipal principal) {
            Integer quotaRemaining = null;
            Integer quotaLimit = null;
            // Kontingent nur für USER-Keys anzeigen — SUPERUSER hat keinen Check (Konzept Kap. 4).
            if (principal.role() == Role.USER && principal.hasKey()) {
                quotaRemaining = accessKeyUsageService.remainingToday(principal.accessKeyId());
                quotaLimit = accessKeyRepository.findById(principal.accessKeyId())
                    .map(AccessKey::getQuotaLimit)
                    .orElse(null);
            }
            return new AuthStatusDto(true, principal.role(), principal.hasKey(),
                principal.accessKeyId(), quotaRemaining, quotaLimit);
        }
        return new AuthStatusDto(false, null, false, null, null, null);
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
            .filter(c -> COOKIE_NAME.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst();
    }

    private void setCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(maxAge)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
