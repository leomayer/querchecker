# Berechtigungskonzept — Implementierungs-Prompt P2 (Session-Cookie-Modell)

> Scope: Auth-Laufzeit — Login-with-Key, Session-Cookie, `QuerCheckerPrincipal`, Security-Filter-Kette, Key-Verwaltung. Baut auf P1 auf (`Role`, `AccessKey`, `AccessKeyService`, `generate-key`, `keys`-Übersicht müssen bereits umgesetzt sein).
> Grundlage: `berechtigungen-konzept.md`, Kap. 2 (Laufzeit-Modell) + Kap. 3 (Session-Modell). KEIN JWT, KEIN Refresh-Token — bewusste Entscheidung, siehe Konzept.

---

## Ziel dieses Schritts

1. `UserSession`-Entity + Flyway-Migration
2. `QuerCheckerPrincipal`
3. `AuthService`: Login (Key → Session-Cookie), Logout, Status (`me`)
4. Drei Security-Filter (Local-Profil → IP-Allowlist → Session-Cookie) + `SecurityConfig`
5. Key-Verwaltung: `PATCH`, `revoke`/`unrevoke`
6. Sliding Expiration + Session-Cleanup
7. Ablösung der bestehenden HTTP-Basic-Auth
8. Tests

**Explizit außerhalb des Scopes:**
- Angular-UI (Status-Element, Zugriffsverwaltung) → P3
- `AccessKeyUsage` / Kontingent-Zählung → P4 (offene Frage: Abzugs-Zeitpunkt)

---

## 1. Konfiguration

`application.yml`:
```yaml
auth:
  session-days: 30
  sliding-extension-hours: 24   # expiresAt nur verlängern, wenn letzte Verlängerung länger her
  superuser-ips: []             # leer = IP-Filter inaktiv; Cloud-Bootstrap: eigene IP eintragen
server:
  forward-headers-strategy: framework   # korrektes RemoteAddr hinter Traefik
```
Kein neuer Eintrag in `secret.yml` nötig (kein JWT-Secret — Session-Tokens sind zufällig und nur als Hash gespeichert).

## 2. `QuerCheckerPrincipal`

```java
package at.querchecker.auth;

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
}
```

## 3. `UserSession`-Entity + Migration

```java
package at.querchecker.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "user_session")
@Getter @Setter
@NoArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String tokenHash; // SHA-256 des Session-Tokens (gleiches Prinzip wie AccessKey.secretKeyHash)

    @Column(nullable = false)
    private Long accessKeyId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
```

Flyway (nächste freie Version im Repo prüfen — nach P1 vermutlich `V32__create_user_session.sql`):

```sql
CREATE TABLE user_session (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    access_key_id BIGINT NOT NULL REFERENCES access_key(id),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_session_access_key ON user_session(access_key_id);
```

Repository: `findByTokenHash(String)`, `deleteByAccessKeyId(Long)`, `deleteByExpiresAtBefore(Instant)`.

## 4. `AuthService`

```java
// login(submittedKey, HttpServletResponse):
// 1. sha256Hex(submittedKey) -> AccessKeyRepository.findBySecretKeyHash
// 2. nicht gefunden ODER revoked -> 401, identische Fehlermeldung
//    (kein Information-Leak, ob der Key existiert)
// 3. AccessKey: used=true, lastUsedAt=now
// 4. Session-Token: UUID.randomUUID().toString()
//    -> sha256Hex in user_session speichern, expiresAt = now + session-days
// 5. Cookie setzen: Name "qc_session", Wert = Klartext-Token,
//    HttpOnly, Secure, SameSite=Strict, Path=/, Max-Age = session-days
//    (ResponseCookie-Builder verwenden)
// 6. Response-Body: { "role": "USER" } — Frontend braucht nur den Status

// logout(request, response):
// Session-Zeile löschen (via Cookie-Hash), Cookie mit Max-Age=0 leeren. Immer 200.

// me(): aus dem SecurityContext:
// - Principal vorhanden -> { "role": "USER"|"SUPERUSER", "authenticated": true }
// - sonst -> { "authenticated": false }  (GUEST)
```

Endpoints (`AuthController`):
- `POST /api/auth/login-with-key` — Body-Record `{ "key": "..." }`
- `POST /api/auth/logout`
- `GET /api/auth/me` — für Angular-Statusanzeige und Session-Wiederherstellung nach F5

## 5. Security-Filter-Kette

Reihenfolge = Priorität; wenn ein früherer Filter einen Principal gesetzt hat, überspringen die späteren.

**Filter 1 — `LocalProfileAuthFilter`** (Bean nur bei `@Profile("local")`):
Setzt für JEDE Anfrage `QuerCheckerPrincipal.withoutKey(Role.SUPERUSER)` + Authority `ROLE_SUPERUSER`.

**Filter 2 — `IpAllowlistAuthFilter`:**
- Allowlist aus Konfiguration (`auth.superuser-ips`); leer = inaktiv
- Remote-IP via `request.getRemoteAddr()` (Forward-Headers-Strategy greift) — `X-Forwarded-For` NICHT manuell parsen (Spoofing-Risiko)
- Match → `withoutKey(Role.SUPERUSER)` + `ROLE_SUPERUSER`

**Filter 3 — `SessionCookieAuthFilter`** (`OncePerRequestFilter`):
- Cookie `qc_session` lesen; fehlt → Kette weiterlaufen lassen (kein 401 hier)
- sha256Hex(Cookie-Wert) → `findByTokenHash`
- Nicht gefunden ODER `expiresAt < now` → Kette weiterlaufen lassen (Request wird GUEST)
- Zugehörigen `AccessKey` laden; `revoked` → ebenfalls GUEST (Sperre greift sofort)
- Erfolg: `withKey(role, accessKeyId)` + `ROLE_USER` bzw. `ROLE_SUPERUSER`
- **Sliding Expiration:** wenn `expiresAt - now < session-days - sliding-extension-hours`, dann `expiresAt = now + session-days` speichern (max. 1 Write pro `sliding-extension-hours`)

**`SecurityConfig`:**
- `@EnableMethodSecurity`
- Bestehende HTTP-Basic-Auth ENTFERNEN
- Endpoint-Regeln (genau zwei Prüf-Ebenen, Konzept Kap. 1):
  - `permitAll`: normale Willhaben-Suche + statische Assets (konkrete Pfade im Repo identifizieren), `POST /api/auth/login-with-key`, `POST /api/auth/logout`, `GET /api/auth/me`
  - AI-/Brave-Endpoints (Spec-Lookup, DL-Extraktion — Pfade im Repo identifizieren): `authenticated()` — jede gültige Session (USER wie SUPERUSER)
  - Settings-Spezialteile (Key-Verwaltung, Usage-Monitor, Provider-Config): `hasRole('SUPERUSER')`
- CSRF deaktiviert lassen, aber Kommentar: Schutz via `SameSite=Strict` am Session-Cookie
- CORS/Dev-Betrieb: Angular-Dev-Server (Port 14072) vs. Backend (14070) sind unterschiedliche Origins → ohne Maßnahme kommt das Cookie im Dev nicht an. Bevorzugte Lösung: Angular-Dev-Server-Proxy (`proxy.conf.json` auf 14070), falls nicht vorhanden anlegen; Alternative: CORS mit `allowCredentials(true)` + expliziter Dev-Origin

## 6. Key-Verwaltung (Erweiterung P1-Controller)

- `PATCH /api/auth/keys/{id}` — Record-DTO mit optionalen Feldern `role`, `quotaLimit` (nur gesetzte Felder ändern; `quotaLimit` ist am Entity NOT NULL, ein „Zurücksetzen auf Default" existiert nicht — es gibt keinen Default)
- `POST /api/auth/keys/{id}/revoke` — `revoked=true` + `deleteByAccessKeyId` auf `user_session` (Sperre wirkt sofort)
- `POST /api/auth/keys/{id}/unrevoke` — `revoked=false` (Sessions entstehen erst beim nächsten Login neu)
- Alle: `@PreAuthorize("hasRole('SUPERUSER')")`

## 7. Session-Cleanup

`@Scheduled`-Job (täglich, z.B. 03:00): `deleteByExpiresAtBefore(now)`. Hinweis im Code: derselbe Job wird in P4 um die `AccessKeyUsage`-Retention (Konzept Kap. 7, DSGVO) erweitert.

## 8. Tests

**`AuthServiceTest`:**
- Login gültig: Session-Zeile mit Hash (nie Klartext) angelegt, Cookie-Attribute korrekt (HttpOnly, Secure, SameSite=Strict, Max-Age), Body enthält Rolle
- Login unbekannter Key vs. revoked Key: identische 401-Antwort
- Logout: Session gelöscht, Cookie geleert
- `me` mit/ohne Principal

**`SessionCookieAuthFilterTest`:**
- Gültige Session → Principal mit korrekter Rolle + accessKeyId
- Abgelaufene Session → kein Principal (GUEST), kein Fehler
- Session gültig, aber AccessKey revoked → kein Principal
- Sliding Expiration: Verlängerung nur außerhalb des Drossel-Intervalls (2 Fälle)

**Filter-Priorität:**
- Local-Profil aktiv → SUPERUSER-Principal ohne Cookie
- IP-Match → SUPERUSER-Principal; Filter 3 überschreibt nicht

**`SecurityConfigIT`** (`@SpringBootTest` + `MockMvc`):
- Öffentliche Suche ohne Auth → 200
- AI-Endpoint ohne Session → 401/403
- AI-Endpoint mit gültigem USER-Session-Cookie → am Security-Layer durchgelassen
- `generate-key` mit USER-Session → 403
- `generate-key` mit aktivem `local`-Profil ohne Cookie → 200
- Nach `revoke`: bestehende Session des Keys → sofort GUEST

---

## Hinweise für den Agent

- Bestehende Security-/`AppConfig`-Struktur im Repo zuerst sichten; Konfigurationsschlüssel an vorhandene Konventionen anpassen
- Konkrete Endpoint-Pfade (öffentliche Suche vs. AI vs. Settings) aus dem Code ermitteln, nicht raten — im Zweifel als TODO markieren und im Ergebnis auflisten
- Nach Umsetzung: `berechtigungen-konzept.md` Kap. 8 aktualisieren

## Nach diesem Schritt (P3/P4 Ausblick, nicht jetzt umsetzen)

- P3: Angular — Status-Element in Toolbar/Footer mit Code-Eingabe (Default = GUEST, kein Login-Screen), `AuthService` (Signal, gespeist aus `GET /api/auth/me` beim App-Start), AI-UI-Elemente statusabhängig ein-/ausblenden, Zugriffsverwaltungs-UI laut Konzept. KEIN Token-Handling, KEIN localStorage, KEIN Auth-Interceptor nötig (Cookie macht alles)
- P4: `AccessKeyUsage` + Kontingent-Zählung mit Ein-Query-Check (Konzept Kap. 4, SQL-Beispiel dort) + Retention-Erweiterung des Cleanup-Jobs
