package at.querchecker.auth;

import org.springframework.security.core.context.SecurityContextHolder;

public record QuerCheckerPrincipal(Role role, Long accessKeyId) {

    public static QuerCheckerPrincipal withoutKey(Role role) {
        return new QuerCheckerPrincipal(role, null);
    }

    public static QuerCheckerPrincipal withKey(Role role, Long accessKeyId) {
        return new QuerCheckerPrincipal(role, accessKeyId);
    }

    public boolean hasKey() {
        return accessKeyId != null;
    }

    /**
     * accessKeyId der aktuellen USER-Session für Traceability-Zwecke (nicht Kontingent-Buchung,
     * siehe {@link at.querchecker.auth.AccessKeyUsageService}). {@code null} für SUPERUSER (auch
     * dev/local-profile), GUEST und für Aufrufe von Threads ohne SecurityContext (z.B. der
     * DL-Extraction-Hintergrund-Pool) — dort ist kein Request-Principal vorhanden.
     */
    public static Long resolveCurrentAccessKeyId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof QuerCheckerPrincipal p && p.role() == Role.USER) {
            return p.accessKeyId();
        }
        return null;
    }
}
