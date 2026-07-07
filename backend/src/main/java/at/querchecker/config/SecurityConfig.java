package at.querchecker.config;

import at.querchecker.auth.LocalProfileAuthFilter;
import at.querchecker.auth.SessionCookieAuthFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Zwei Prüf-Ebenen (Konzept Kap. 1): AI-/Brave-Endpoints brauchen nur eine gültige Session
// (USER oder SUPERUSER), Settings-Spezialteile brauchen SUPERUSER. Alles andere bleibt offen.
// Filter-Kette: LocalProfileAuthFilter (nur Nicht-Prod) -> SessionCookieAuthFilter -> Spring Security.
// Kein IP-Allowlist-Filter (siehe berechtigungen-konzept.md: noch kein Traefik/Cloud-Setup —
// erster SUPERUSER-Key wird per manuellem SQL-Insert erzeugt, siehe docs/auth).
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final SessionCookieAuthFilter sessionCookieAuthFilter;
    private final ObjectProvider<LocalProfileAuthFilter> localProfileAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Schutz gegen CSRF kommt über SameSite=Strict am Session-Cookie, kein Token nötig.
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(sessionCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);

        LocalProfileAuthFilter localFilter = localProfileAuthFilter.getIfAvailable();
        if (localFilter != null) {
            http.addFilterBefore(localFilter, SessionCookieAuthFilter.class);
            log.info("[Auth] Profil '!prod' aktiv — jede Anfrage laeuft automatisch als SUPERUSER (kein Access-Key noetig)");
        } else {
            log.info("[Auth] Profil 'prod' aktiv — Zugriff nur ueber gueltigen Access-Key (Session-Cookie)");
        }

        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/auth/login-with-key", "/api/auth/logout", "/api/auth/me"
            ).permitAll()
            // Read-only Kategorie-Präferenzen (Quick-Facts-Feldreihenfolge) sind kein
            // Settings-Spezialteil (Konzept Kap. 1) — jeder Nutzer inkl. GUEST braucht sie
            // beim Anzeigen der Produktanalyse. Nur das Speichern (PUT) ist SUPERUSER-only.
            .requestMatchers(HttpMethod.GET, "/api/settings/preferences").permitAll()
            .requestMatchers(
                "/api/auth/generate-key", "/api/auth/keys", "/api/auth/keys/**",
                "/api/usage/**", "/api/provider-setup/**", "/api/provider-status/**",
                "/api/admin/**", "/api/dl/settings/**", "/api/settings/preferences/**"
            ).hasRole("SUPERUSER")
            .requestMatchers(
                "/api/listings/*/lookup/**", "/api/dl/extraction/**"
            ).authenticated()
            .anyRequest().permitAll()
        );

        return http.build();
    }
}
