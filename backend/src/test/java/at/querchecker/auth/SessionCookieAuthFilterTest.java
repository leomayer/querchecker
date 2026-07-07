package at.querchecker.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCookieAuthFilterTest {

    @Mock UserSessionRepository userSessionRepository;
    @Mock AccessKeyRepository accessKeyRepository;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    private final AuthProperties authProperties = new AuthProperties();
    private SessionCookieAuthFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UserSession session(Instant expiresAt) {
        UserSession s = new UserSession();
        s.setId(1L);
        s.setAccessKeyId(5L);
        s.setExpiresAt(expiresAt);
        return s;
    }

    private AccessKey key(Role role, boolean revoked) {
        AccessKey k = new AccessKey();
        k.setId(5L);
        k.setRole(role);
        k.setRevoked(revoked);
        return k;
    }

    @Test
    void validSession_setsPrincipalWithRoleAndAccessKeyId() throws Exception {
        filter = new SessionCookieAuthFilter(userSessionRepository, accessKeyRepository, authProperties);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("qc_session", "raw-token")});
        when(userSessionRepository.findByTokenHash(DigestUtils.sha256Hex("raw-token")))
            .thenReturn(Optional.of(session(Instant.now().plus(Duration.ofDays(29)))));
        when(accessKeyRepository.findById(5L)).thenReturn(Optional.of(key(Role.USER, false)));

        filter.doFilter(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        var principal = (QuerCheckerPrincipal) auth.getPrincipal();
        assertThat(principal.role()).isEqualTo(Role.USER);
        assertThat(principal.accessKeyId()).isEqualTo(5L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void expiredSession_leavesGuestNoException() throws Exception {
        filter = new SessionCookieAuthFilter(userSessionRepository, accessKeyRepository, authProperties);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("qc_session", "raw-token")});
        when(userSessionRepository.findByTokenHash(any())).thenReturn(Optional.of(session(Instant.now().minusSeconds(60))));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void validSessionButRevokedAccessKey_leavesGuest() throws Exception {
        filter = new SessionCookieAuthFilter(userSessionRepository, accessKeyRepository, authProperties);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("qc_session", "raw-token")});
        when(userSessionRepository.findByTokenHash(any()))
            .thenReturn(Optional.of(session(Instant.now().plus(Duration.ofDays(29)))));
        when(accessKeyRepository.findById(5L)).thenReturn(Optional.of(key(Role.USER, true)));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void noCookie_leavesGuestWithoutRepositoryLookup() throws Exception {
        filter = new SessionCookieAuthFilter(userSessionRepository, accessKeyRepository, authProperties);
        when(request.getCookies()).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userSessionRepository, never()).findByTokenHash(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void slidingExpiration_extendsOnlyWhenCloseToExpiry() throws Exception {
        filter = new SessionCookieAuthFilter(userSessionRepository, accessKeyRepository, authProperties);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("qc_session", "raw-token")});
        // sessionDays=30, slidingExtensionHours=24 -> extend only if remaining < 29 days.
        // Far from expiry (29.5 days left) -> no extension write.
        when(userSessionRepository.findByTokenHash(any()))
            .thenReturn(Optional.of(session(Instant.now().plus(Duration.ofHours(29 * 24 + 12)))));
        when(accessKeyRepository.findById(5L)).thenReturn(Optional.of(key(Role.USER, false)));

        filter.doFilter(request, response, chain);

        verify(userSessionRepository, never()).save(any());
    }

    @Test
    void slidingExpiration_extendsWhenWithinThrottleWindow() throws Exception {
        filter = new SessionCookieAuthFilter(userSessionRepository, accessKeyRepository, authProperties);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("qc_session", "raw-token")});
        // Only 1 hour left -> well within the last sliding-extension-hours window -> must extend.
        when(userSessionRepository.findByTokenHash(any()))
            .thenReturn(Optional.of(session(Instant.now().plus(Duration.ofHours(1)))));
        when(accessKeyRepository.findById(5L)).thenReturn(Optional.of(key(Role.USER, false)));

        filter.doFilter(request, response, chain);

        verify(userSessionRepository, times(1)).save(any());
    }

    @Test
    void alreadyAuthenticated_skipsSessionLookup() throws Exception {
        filter = new SessionCookieAuthFilter(userSessionRepository, accessKeyRepository, authProperties);
        var existing = new org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken(
            QuerCheckerPrincipal.withoutKey(Role.SUPERUSER), null,
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SUPERUSER")));
        existing.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(existing);

        filter.doFilter(request, response, chain);

        verify(userSessionRepository, never()).findByTokenHash(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}
