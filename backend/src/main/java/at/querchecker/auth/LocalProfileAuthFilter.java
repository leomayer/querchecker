package at.querchecker.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Dev-Komfort: jede Anfrage läuft als SUPERUSER, solange das Backend NICHT mit dem "prod"-Profil
// läuft (docker-compose.prod.yml setzt SPRING_PROFILES_ACTIVE=prod). Lokales `mvn spring-boot:run`
// hat kein Profil gesetzt -> Filter aktiv. Kein separates "local"-Profil nötig.
@Component
@Profile("!prod")
public class LocalProfileAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            var principal = QuerCheckerPrincipal.withoutKey(Role.SUPERUSER);
            var auth = new PreAuthenticatedAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPERUSER")));
            auth.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
