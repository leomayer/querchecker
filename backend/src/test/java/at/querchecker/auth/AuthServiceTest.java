package at.querchecker.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AccessKeyRepository accessKeyRepository;
    @Mock UserSessionRepository userSessionRepository;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    private final AuthProperties authProperties = new AuthProperties();
    private AuthService service;

    private AuthService service() {
        return new AuthService(accessKeyRepository, userSessionRepository, authProperties);
    }

    private AccessKey activeKey(Role role) {
        AccessKey key = new AccessKey();
        key.setId(1L);
        key.setRole(role);
        key.setRevoked(false);
        return key;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_validKey_createsSessionWithHashedTokenAndSetsCookie() {
        service = service();
        when(accessKeyRepository.findBySecretKeyHash(any())).thenReturn(Optional.of(activeKey(Role.USER)));

        service.login("raw-key", response);

        ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(sessionCaptor.capture());
        UserSession saved = sessionCaptor.getValue();
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getAccessKeyId()).isEqualTo(1L);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), cookieCaptor.capture());
        String cookie = cookieCaptor.getValue();
        assertThat(cookie).contains("qc_session=").contains("HttpOnly").contains("Secure").contains("SameSite=Strict");
    }

    @Test
    void login_marksAccessKeyUsed() {
        service = service();
        AccessKey key = activeKey(Role.USER);
        when(accessKeyRepository.findBySecretKeyHash(any())).thenReturn(Optional.of(key));

        service.login("raw-key", response);

        ArgumentCaptor<AccessKey> captor = ArgumentCaptor.forClass(AccessKey.class);
        verify(accessKeyRepository).save(captor.capture());
        assertThat(captor.getValue().isUsed()).isTrue();
        assertThat(captor.getValue().getLastUsedAt()).isNotNull();
    }

    @Test
    void login_returnsRoleFromAccessKey() {
        service = service();
        when(accessKeyRepository.findBySecretKeyHash(any())).thenReturn(Optional.of(activeKey(Role.SUPERUSER)));

        var result = service.login("raw-key", response);

        assertThat(result.role()).isEqualTo(Role.SUPERUSER);
    }

    @Test
    void login_unknownKey_and_revokedKey_giveIdenticalUnauthorizedMessage() {
        service = service();
        when(accessKeyRepository.findBySecretKeyHash(any())).thenReturn(Optional.empty());
        String unknownMessage = catchReason(() -> service.login("unknown", response));

        AccessKey revoked = activeKey(Role.USER);
        revoked.setRevoked(true);
        when(accessKeyRepository.findBySecretKeyHash(any())).thenReturn(Optional.of(revoked));
        String revokedMessage = catchReason(() -> service.login("revoked-key", response));

        assertThat(unknownMessage).isNotBlank().isEqualTo(revokedMessage);
        verify(userSessionRepository, never()).save(any());
    }

    private String catchReason(Runnable action) {
        try {
            action.run();
        } catch (ResponseStatusException e) {
            return e.getReason();
        }
        throw new AssertionError("expected ResponseStatusException to be thrown");
    }

    @Test
    void logout_deletesMatchingSessionAndClearsCookie() {
        service = service();
        String rawToken = "session-token";
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("qc_session", rawToken)});
        UserSession existing = new UserSession();
        existing.setId(9L);
        when(userSessionRepository.findByTokenHash(DigestUtils.sha256Hex(rawToken))).thenReturn(Optional.of(existing));

        service.logout(request, response);

        verify(userSessionRepository).delete(existing);
        ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), cookieCaptor.capture());
        assertThat(cookieCaptor.getValue()).contains("Max-Age=0");
    }

    @Test
    void logout_withoutCookie_stillReturnsAndClearsCookie() {
        service = service();
        when(request.getCookies()).thenReturn(null);

        service.logout(request, response);

        verify(userSessionRepository, never()).delete(any(UserSession.class));
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), any());
    }

    @Test
    void me_withAuthenticatedPrincipal_returnsRole() {
        service = service();
        var principal = QuerCheckerPrincipal.withKey(Role.USER, 1L);
        var auth = new PreAuthenticatedAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        var status = service.me();

        assertThat(status.authenticated()).isTrue();
        assertThat(status.role()).isEqualTo(Role.USER);
    }

    @Test
    void me_withoutAuthentication_returnsGuest() {
        service = service();

        var status = service.me();

        assertThat(status.authenticated()).isFalse();
        assertThat(status.role()).isNull();
    }
}
