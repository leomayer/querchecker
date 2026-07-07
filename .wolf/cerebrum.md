# Cerebrum

> OpenWolf's learning memory. Updated automatically as the AI learns from interactions.
> Do not edit manually unless correcting an error.
> Last updated: 2026-04-19

## User Preferences

<!-- How the user likes things done. Code style, tools, patterns, communication. -->

## Key Learnings

- **Project:** querchecker
- **Description:** Querchecker entstand aus einem konkreten Bedarf: die Suche nach einem gebrauchten Drucker auf dem Willhaben Marktplatz (Kleinanzeigen). Welche Modelle haben noch verfügbare Patronen? Gibt es Ersatztei

## Do-Not-Repeat

<!-- Mistakes made and corrected. Each entry prevents the same mistake recurring. -->
<!-- Format: [YYYY-MM-DD] Description of what went wrong and what to do instead. -->

## Decision Log

<!-- Significant technical decisions with rationale. Why X was chosen over Y. -->

- **[2026-07-07] P1 Berechtigungskonzept (docs/auth/):** Doc assumed Spring Security + HTTP Basic Auth already existed in the project — it didn't (no `spring-boot-starter-security`, no security config anywhere, not even in traefik). Added `spring-boot-starter-security` + `commons-codec` (for `DigestUtils.sha256Hex`) to `backend/pom.xml`, plus minimal `at.querchecker.config.SecurityConfig` (`@EnableMethodSecurity`, `permitAll()` everywhere except `/api/auth/**` which is `authenticated()`). `@PreAuthorize("hasRole('SUPERUSER')")` on `AccessKeyController` is a placeholder — no filter grants `ROLE_SUPERUSER` yet (comes in P2's session/local-profile/IP filters), so `/api/auth/generate-key` and `/api/auth/keys` return 403 for everyone until P2 lands. User explicitly chose this over skipping the security dependency for now (asked via AskUserQuestion). New package: `at.querchecker.auth` (`Role`, `AccessKey`, `AccessKeyRepository`, `AccessKeyService`, `AccessKeyController`, `auth.dto.*`). Migration `V42__create_access_key.sql`.

- **[2026-07-07] P2 Berechtigungskonzept — Auth-Laufzeit implementiert, drei Abweichungen von docs/auth/berechtigung-P2-auth-laufzeit.md, alle User-Entscheidung:** (1) **Kein IP-Allowlist-Filter** — Projekt hat noch kein Traefik/Cloud-Setup (docker-compose.prod.yml ist die einzige Prod-Config, kein Reverse-Proxy), Admin-IP wäre vermutlich ohnehin dynamisch (Heimnetz) → ersatzlos gestrichen, nur zwei Filter (`LocalProfileAuthFilter` + `SessionCookieAuthFilter`). (2) **`@Profile("!prod")` statt `@Profile("local")`** — Projekt hat nie ein `local`-Profil gehabt, nur `prod` (docker-compose.prod.yml setzt `SPRING_PROFILES_ACTIVE=prod`) vs. Default (normaler `mvn spring-boot:run`-Dev-Betrieb ohne Profil). `!prod` matcht genau das ohne neues Profil einzuführen. (3) **Kein Bootstrap-Code für den ersten SUPERUSER-Key** — erst Env-Var-Bootstrap geplant, dann verworfen: einfacher manueller SQL-Insert (Hash selbst bilden, `INSERT INTO access_key`, siehe `berechtigungen-konzept.md` Kap. 3) reicht, solange kein Cloud-Deployment existiert. **Endpoint-Klassifizierung:** komplettes Settings-Menü ist SUPERUSER-only *außer* Font-Größe (rein clientseitig, kein Backend-Zugriff nötig) — das war die konkrete User-Vorgabe, die die sonst mehrdeutige Einordnung von `/api/settings/preferences`, `/api/dl/settings`, `/api/provider-status/**` auflöste. **Test-Gotcha:** `@MockBean SessionCookieAuthFilter` in `@WebMvcTest` bricht die komplette Filter-Kette (Mockito mockt `doFilter()` selbst zu einem No-Op, der nie `chain.doFilter()` aufruft → jede Anfrage bekommt eine leere 200-Antwort, auch „isForbidden"-Tests). Fix: die echten Repository-Abhängigkeiten der Filter mocken (`UserSessionRepository`, `AccessKeyRepository`) statt den Filter selbst, plus `@ActiveProfiles("prod")` im Test, damit `LocalProfileAuthFilter` (`@Profile("!prod")`) im Web-Slice nicht automatisch jede Anfrage zu SUPERUSER macht.
