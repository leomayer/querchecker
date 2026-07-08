# Querchecker — Berechtigungs- & Kontingent-Konzept

> Status: P1–P4 implementiert. Ebene-2-Kontingent (Key-Kontingent) läuft. Ursprünglicher Implementierungs-Prompt: `berechtigung-P4-kontingent-zaehlung.md`.
>
> **Abweichung von der ursprünglichen Konzeption (siehe Kap. 8):** Kein IP-Allowlist-Filter, kein separates `local`-Profil — es gibt noch kein Traefik/Cloud-Setup, daher wurde die Filter-Kette auf zwei Filter reduziert (`@Profile("!prod")` statt `local`, Session-Cookie). Bootstrap des ersten SUPERUSER-Keys ausschließlich per manuellem SQL-Insert.

---

## Kapitel 1 — Motivation & Grundmodell

Aktuell (Single-User, HTTP Basic Auth) gilt Kontingent global. Für den öffentlichen Betrieb gilt künftig ein bewusst minimales Modell mit **drei Zuständen**:

| Zustand | Wie erkannt | AI-Zugriff | Kontingent | Settings-Spezialteile |
|---|---|---|---|---|
| `GUEST` | keine Session | ❌ (nur normale Suche) | – | ❌ |
| `USER` | Session via Zugriffskey | ✅ | individuell pro Key (`quotaLimit`) | ❌ |
| `SUPERUSER` | Session via Key **oder** Nicht-Prod-Profil (`!prod`) | ✅ | unbegrenzt (kein Check) | ✅ |

**Es gibt genau zwei Prüf-Stellen:**
1. **AI-/Brave-Zugriffe:** gültige Session ja/nein → danach Kontingent-Check (Kap. 4)
2. **Settings-Spezialteile** (Key-Verwaltung, Usage-Monitor, Provider-Config): `SUPERUSER` ja/nein

Alles andere (normale Suche, allgemeine UI) ist öffentlich. Keine Settings-Matrix, keine Zwischenrollen.

**Kontingent ist eine Eigenschaft des Keys, nicht der Rolle.** Es gibt kein „Premium" als Rollenwert — ein großzügiger ausgestatteter User ist einfach ein Key mit höherem `quotaLimit`. Ob das Limit erreicht ist, rechnet die DB in einer einzigen Query aus (Kap. 4).

---

## Kapitel 2 — Rollen & Laufzeit-Modell

```java
public enum Role {
    USER,      // Kontingent = AccessKey.quotaLimit, DB entscheidet (Kap. 4)
    SUPERUSER  // kein Kontingent-Check, Settings-Spezialteile erlaubt
}
```

**`GUEST` und `LOCAL` sind bewusst keine Enum-Werte:**
- **`GUEST`** = „keine Authentication vorhanden". Wo kein Principal gesetzt ist, gelten automatisch nur die öffentlichen Endpoints.
- **`LOCAL`** = Laufzeit-Erkennung per Spring-Profil, bekommt zur Laufzeit `Role.SUPERUSER` zugewiesen — identische Rechte, kein `AccessKey`-Eintrag.

### Erkennung `LOCAL` — einmalig gesetzt, nicht pro Request geprüft
**Spring-Profil**, `@Profile("!prod")` statt eines eigenen `local`-Profils — das Projekt kennt bisher nur `prod` (via `SPRING_PROFILES_ACTIVE=prod` in `docker-compose.prod.yml`) und sonst default. Läuft der Backend-Prozess NICHT mit `prod`-Profil (also jeder normale `mvn spring-boot:run`-Dev-Betrieb), bekommt jede Anfrage automatisch `SUPERUSER`-Rechte. Semantik: „diese Instanz läuft nicht als Produktions-Deployment".

### `SUPERUSER` remote — nur Key-basiert
Kein IP-Allowlist-Filter (siehe Kap. 8 für die Begründung) — in Prod ausschließlich **Key-basiert** — regulärer `AccessKey` mit `role = SUPERUSER`, per `POST /api/auth/login-with-key` eingeloggt.

### Laufzeit-Modell: `QuerCheckerPrincipal`

Die Business-Logik fragt Rechte **immer** über dieselbe Struktur ab, egal wie die Rolle zustande kam:

```java
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

**Zwei Filter, eine gemeinsame Ziel-Struktur** (Reihenfolge = Priorität; hat der erste gesetzt, überspringt der zweite):

| # | Filter | Aktiv wenn | Ergebnis |
|---|---|---|---|
| 1 | `LocalProfileAuthFilter` | Spring-Profil `!prod` aktiv | `withoutKey(SUPERUSER)` für jede Anfrage |
| 2 | `SessionCookieAuthFilter` | Gültige Session in DB (Kap. 3) | `withKey(role, accessKeyId)` |
| – | keiner greift | — | keine Authentication → `GUEST` (nur öffentliche Endpoints) |

Alle Filter setzen die passende `GrantedAuthority` (`ROLE_SUPERUSER` / `ROLE_USER`), sodass `@PreAuthorize("hasRole('SUPERUSER')")` unabhängig vom Auth-Weg funktioniert.

**Konsequenz für den Kontingent-Check (Kap. 4):** Bei `role == SUPERUSER` (egal ob mit oder ohne Key) wird die Kontingent-Prüfung komplett übersprungen.

---

## Kapitel 3 — Authentifizierung: Key-Login → Session-Cookie

**Entscheidung: Session-Cookie-Modell statt JWT+Refresh.** Begründung: Der Stateless-Vorteil von JWT ist hier minimal — jede AI-Anfrage braucht ohnehin einen DB-Zugriff (Kontingent, Kap. 4), und für die einzige weitere Prüfung (`SUPERUSER` ja/nein) lohnt keine Token-Infrastruktur. Das Session-Modell spart ersatzlos: JWT-Dependency, JWT-Secret, Refresh-Endpoint, Token-Rotation, 401-Retry-Interceptor. Bonus: **Sperren greifen sofort** (DB-Check pro Request).

### Ablauf
1. Superuser generiert `AccessKey` (Rolle + Kontingent) — `POST /api/auth/generate-key` (P1)
2. User gibt den Key einmalig ein → `POST /api/auth/login-with-key` (Key im **Body**, nie als URL-Parameter — sonst landet er in Server-/Proxy-Logs und Browser-Historie)
3. Server: Hash-Lookup → gültig? → Session-Eintrag anlegen, Session-Token als **HttpOnly-Cookie** setzen
4. Ab dann schickt der Browser das Cookie automatisch mit — Angular verwaltet **keinerlei** Token

### `AccessKey`-Entity (P1)

```java
@Entity
@Table(name = "access_key")
@Getter @Setter
@NoArgsConstructor
public class AccessKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String secretKeyHash; // SHA-256, NIE der Klartext

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // USER oder SUPERUSER

    @Column(nullable = false)
    private int quotaLimit; // Tages-Kontingent dieses Keys; bei SUPERUSER ohne Wirkung (kein Check)

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant lastUsedAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(nullable = false)
    private boolean revoked = false;
}
```

**Generierung & Prüfung:**
```java
// Generieren: rawKey = UUID.randomUUID().toString();
//   gespeichert wird NUR DigestUtils.sha256Hex(rawKey);
//   rawKey einmalig in der Response, nie persistiert
// Login: sha256Hex(submittedKey) -> findBySecretKeyHash
```
SHA-256 statt BCrypt reicht: der Key ist hochentropisch, kein Wörterbuch-Angriff möglich.

### `UserSession`-Entity (P2)

```java
@Entity
@Table(name = "user_session")
@Getter @Setter
@NoArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String tokenHash; // SHA-256 des Session-Tokens

    @Column(nullable = false)
    private Long accessKeyId; // FK auf access_key.id

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
```

### Session-Mechanik
- **Cookie:** `qc_session`, `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`, `Max-Age` = Session-Laufzeit (z.B. 30 Tage). Für JavaScript unsichtbar — Angular kennt nur den Login-Status via `GET /api/auth/me`.
- **Prüfung pro Request (Filter 3):** Cookie → SHA-256 → `user_session`-Lookup (indiziert) → `expiresAt` prüfen → `AccessKey` laden → `revoked`? → Principal setzen. Ein indizierter Lookup pro authentifiziertem Request — irrelevant bei diesem Traffic, dafür wirken Sperren sofort.
- **Sliding Expiration statt Refresh:** Bei Nutzung wird `expiresAt` verlängert — gedrosselt (nur wenn letzte Verlängerung > 24h her), damit nicht jeder Request ein DB-Write wird. Effekt: Wer die Seite mindestens alle 30 Tage nutzt, bleibt eingeloggt; danach Key neu eingeben.
- **Ablauf serverseitig maßgeblich:** `Max-Age` im Browser ist nur Komfort — die Wahrheit ist `expiresAt` in der DB.
- **Logout:** `POST /api/auth/logout` löscht die Session-Zeile + leert das Cookie.
- **Key-Sperre:** `revoke` löscht zusätzlich alle Sessions des Keys → wirkt sofort.
- **CSRF:** `SameSite=Strict` deckt das Setup ab; als Kommentar in der `SecurityConfig` dokumentieren.
- **Cleanup:** Abgelaufene Sessions räumt derselbe Cronjob ab wie die DSGVO-Löschung (Kap. 7).

### Endpoint-Absicherung (die einzigen zwei Prüf-Stellen, Kap. 1)
- **Öffentlich (`permitAll`):** normale Willhaben-Suche, statische Assets, `login-with-key`, `logout`, `me`
- **AI-/Brave-Endpoints:** `authenticated()` — jede gültige Session; danach greift Kap. 4
- **Settings-Spezialteile:** `hasRole('SUPERUSER')`

### Key-Verwaltung
- `PATCH /api/auth/keys/{id}` — Rolle oder `quotaLimit` ändern
- `POST /api/auth/keys/{id}/revoke` / `.../unrevoke` — Soft-Delete, History bleibt (Kap. 6); Revoke löscht Sessions des Keys
- `GET /api/auth/keys` — Übersicht (P1), ohne Secret/Hash
- Alle: `hasRole('SUPERUSER')`

### Bootstrapping bei Cloud-Hosting
Cloud-Profil ist `prod` → `LocalProfileAuthFilter` inaktiv. Noch kein Traefik/Cloud-Setup vorhanden, daher bewusst **kein** IP-Allowlist-Filter (Bootstrap-Problem wäre ohnehin nur mit stabiler IP lösbar). Erster `SUPERUSER`-Zugriff ausschließlich manuell:
- Klartext-Key selbst wählen (z.B. `openssl rand -hex 32` oder eine UUID)
- Hash bilden: `echo -n "<key>" | sha256sum`
- Zeile direkt einfügen:
  ```sql
  INSERT INTO access_key (secret_key_hash, role, quota_limit, used, revoked)
  VALUES ('<sha256-hex>', 'SUPERUSER', 0, false, false);
  ```
- Danach mit dem gewählten Klartext-Key ganz normal per `POST /api/auth/login-with-key` einloggen

### UI-Konzept: Settings → Zugriffsverwaltung

Neue Karte im Settings-Bereich (Expandable-Cards-Refactoring), sichtbar nur für `SUPERUSER`.

**Übersichts-Tabelle** (`mat-table`, analog Usage-Monitor):

```
┌────────────────────────────────────────────────────────────────┐
│ ZUGRIFFSVERWALTUNG                          [+ Neuen Key]      │
│                                                                │
│ Rolle     │ Kontingent │ Erstellt   │ Zuletzt genutzt │ Status │
│ ──────────┼────────────┼────────────┼─────────────────┼────────┤
│ USER      │ 10/Tag     │ 01.07.2026 │ vor 2 Std.      │ Aktiv ⋮│
│ USER      │ 50/Tag     │ 28.06.2026 │ vor 3 Tagen     │ Aktiv ⋮│
│ SUPERUSER │ –          │ 15.06.2026 │ heute           │ Aktiv ⋮│
│ USER      │ 10/Tag     │ 01.05.2026 │ vor 40 Tagen    │ Gesperrt│
└────────────────────────────────────────────────────────────────┘
```
- `⋮`: „Bearbeiten" (PATCH), „Sperren"/„Entsperren"
- Keine Secret-Spalte — nie abrufbar
- Gesperrte Keys gedimmt, bleiben in der Liste (History, Kap. 6)
- Bei `SUPERUSER`-Keys wird das Kontingent als „–" angezeigt (kein Check)

**„Neuen Key generieren"-Dialog** (`mat-dialog`): Rolle-Dropdown (`USER`/`SUPERUSER`) + Kontingent-Feld (Pflicht bei `USER`, Vorschlagswert 10; ausgeblendet bei `SUPERUSER`, dann serverseitig z.B. 0). Nach Bestätigung Ergebnis-Dialog mit dem Klartext-Key, deutlich markiert als **einmalig sichtbar**, mit Kopieren-Button.

**Code-Eingabe als Status-Element (kein Login-Screen):** Default = `GUEST` — volle normale Suche, AI-Funktionen ausgeblendet/deaktiviert. Dezentes Status-Element in Toolbar/Footer (Icon + „Gast") öffnet bei Klick ein Eingabefeld (`mat-menu`) für den Zugriffscode → `POST /login-with-key` → Status zeigt die Rolle. Keine eigene Route, kein erzwungener Login.

**Storage-Entscheidung:** Clientseitig wird **nichts** gespeichert — kein `localStorage`, kein Token im Angular-State. Das HttpOnly-Cookie erledigt alles; nach F5 fragt die App per `GET /api/auth/me` den Login-Status ab. Der rohe Key wird niemals persistiert.

**Usage-Monitor-Erweiterung:** Zweiter, einklappbarer Bereich „Pro Key" (Ebene 2, Kap. 6) — visuell getrennt von der bestehenden Provider-Ansicht (Ebene 1).

---

## Kapitel 4 — Zwei-Ebenen-Kontingent-Modell

Provider-Kontingent (technisch) und Key-Kontingent (User-facing) sind unterschiedliche Dinge und werden getrennt geführt.

### Ebene 1 — Provider-Kontingent (bestehend, unverändert)
- Schutz gegen Erschöpfung externer APIs (Brave, Groq, ...)
- Global, pro Provider — **nicht** pro User
- Bereits implementiert: `QuotaService`, `ApiUsageLog` mit `Provider`-Enum

### Ebene 2 — Key-Kontingent (neu)
- Zählt **Nutzeraktionen**, nicht Provider-Calls: „Specs laden" = 1 Einheit, egal ob intern 2 oder 5 Provider-Calls
- Entity `AccessKeyUsage`:

```java
@Entity
@Getter @Setter
@NoArgsConstructor
public class AccessKeyUsage {
    @Id @GeneratedValue
    private Long id;

    private Long accessKeyId; // FK auf AccessKey.id (aus der Session ermittelt, Kap. 3)
    private LocalDate periodDate; // Tageskontingent
    private int consumedCount;
}
```

- **History-Tabelle, nicht überschreiben** — eine Zeile pro `accessKeyId` + `periodDate` → Auswertung (Kap. 6)
- **Limit & Verbrauch in einer Query** — die DB entscheidet, nicht Java:

```sql
SELECT k.quota_limit - COALESCE(u.consumed_count, 0) AS remaining
FROM access_key k
LEFT JOIN access_key_usage u
       ON u.access_key_id = k.id AND u.period_date = CURRENT_DATE
WHERE k.id = :accessKeyId
```
`remaining <= 0` → `QUOTA_EXCEEDED`. Ein Roundtrip, kein Zusammensetzen aus Enum-Defaults.

- **Skip-Regel:** `role == SUPERUSER` → Check komplett übersprungen (auch für `LOCAL`/IP-Weg, die als `SUPERUSER` laufen)

### Check-Reihenfolge
1. Key-Kontingent (eine Query, s.o.) — **vor** jeder Provider-Pipeline
2. Danach bestehende Pipeline: Cache-Check → Provider-Kontingent → Brave → LLM

### API-Response-Format (Client sieht keine Rohdaten)
Berechnung bleibt vollständig serverseitig; Client erhält nur Accepted/Rejected. Bei Erschöpfung: bewusst minimale Antwort (`QUOTA_EXCEEDED` ohne Datums-/Zeitangabe). Detailzahlen nur im Usage-Monitor (superuser-only).

### Sonderregeln für den Verbrauch
- **Cache-Hit zählt nicht** — keine Provider-Calls, kein Verbrauch
- **Provider-Erschöpfung zählt nicht gegen den User** — global verursacht, nicht von ihm

**Noch offen:** Abzug bei **Start** vs. nach **erfolgreichem Abschluss** der Aktion (Race Conditions bei parallelen Requests beachten). Tendenz: nach Abschluss.

---

## Kapitel 5 — Traffic-Schutz bei mehreren Usern

Das globale Provider-Kontingent (Ebene 1) wirkt bei mehreren Usern automatisch als Flaschenhals — es kann erschöpft sein, obwohl einzelne User ihr persönliches Kontingent noch nicht ausgeschöpft haben.

**Entscheidung (vorerst):** „Provider gesperrt → alle sehen Fehlermeldung", analog Single-User-Verhalten.

**Zurückgestellt:** Dynamisches Reduzieren der Key-Limits bei z.B. ≥80% globalem Verbrauch. `SUPERUSER` wäre davon nicht betroffen (kein Ebene-2-Check), bei technischer Provider-Erschöpfung (Ebene 1) aber genauso blockiert wie alle.

---

## Kapitel 6 — Auswertung / Reporting

`AccessKeyUsage` als History-Tabelle → SQL-Aggregation nach Key/Zeitraum ohne Zusatz-Tracking.

**Für später:** Nächtlicher Rollup-Job in Summary-Tabelle, erst bei echtem Performance-Bedarf.

**Darstellung:** Erweiterung des Usage-Monitor-Patterns (superuser-only) um Key-Breakdown.

---

## Kapitel 7 — Datenaufbewahrung & Löschung

Bei öffentlichem EU/Österreich-Betrieb DSGVO-relevant (personenbezogene Daten: `accessKeyId`, IP, Nutzungshistorie) — kein optionales Extra.

- Lösch-Policy: Cronjob löscht `AccessKeyUsage`-Einträge älter als X Tage (z.B. 90)
- Derselbe Job räumt abgelaufene `user_session`-Zeilen ab (Kap. 3)
- Kein komplexes Anonymisieren — Hard-Delete nach Frist reicht

---

## Kapitel 7a — Umfangs-/Overkill-Einschätzung

**Notwendig (MVP):**
- Drei-Zustands-Modell (Kap. 1/2), zweistufiges Kontingent (Kap. 4), Session-Auth (Kap. 3)
- Lösch-Policy (Kap. 7) — DSGVO-Pflicht

**Bewusst verworfen/zurückgestellt:**
- JWT+Refresh-Infrastruktur — verworfen zugunsten des Session-Modells (Kap. 3)
- Rollen-Default-Kontingent im Enum — verworfen: Kontingent hängt allein am Key, DB rechnet (Kap. 4)
- Zwischenrollen (PREMIUM etc.) — verworfen: „mehr Kontingent" ist nur eine Zahl, kein Rollenwert
- Nächtlicher Rollup-Job (Kap. 6), dynamische Drosselung (Kap. 5) — zurückgestellt

**MVP-Schnitt:** `USER`/`SUPERUSER` + Key-Kontingent (eine Query) + Session-Cookie + Lösch-Policy.

---

## Kapitel 8 — Offene Punkte (für Folge-Session)

```
- [geklärt/erledigt P4] Definition "eine AI-Anfrage" = nur Item-Research-Lookup;
  DL-Extraktion zählt NICHT (keine bewusste User-Aktion)
- [geklärt/erledigt P4] Kontingent-Abzug nach Abschluss (consume nur im Erfolgspfad);
  Race-Condition per atomarem ON-CONFLICT-Upsert abgesichert
- Dynamische Drosselung bei hohem Traffic (Kap. 5) — zurückgestellt
- [erledigt] P4 (Kontingent-Zählung) — siehe P4-Status oben
```

### P1 — Status: abgeschlossen

**Abweichung von der ursprünglichen Konzeption:** Das Konzept-Dokument ging davon aus, dass Spring Security bereits im Projekt vorhanden ist — war nicht der Fall (keine `spring-boot-starter-security`-Dependency, keine `SecurityConfig`, nirgends). Deshalb zusätzlich `spring-boot-starter-security` + `commons-codec` (`DigestUtils.sha256Hex`) zu `backend/pom.xml` hinzugefügt sowie eine minimale `SecurityConfig` (`@EnableMethodSecurity`, `/api/auth/**` = `authenticated()`, alles andere `permitAll()`). `@PreAuthorize("hasRole('SUPERUSER')")` war zu diesem Zeitpunkt ein reiner Platzhalter — kein Filter vergab `ROLE_SUPERUSER`, `generate-key`/`keys` waren also für alle 403. Mit P2 (Session-Cookie-Filter + Local-Profile-Filter) ist das aufgelöst.

### P2 — Status: Backend abgeschlossen

**Bewusste Abweichungen von der ursprünglichen Konzeption (Nutzer-Entscheidungen, 2026-07-07):**
- **Kein IP-Allowlist-Filter.** Noch kein Traefik/Cloud-Setup vorhanden; Admin-IP wäre ohnehin evtl. dynamisch (Heimnetz). Ersatzlos gestrichen statt „nur Komfort-Fallback" — reduziert auf zwei Filter (lokal/prod-Erkennung + Session-Cookie).
- **`@Profile("!prod")` statt eigenem `local`-Profil.** Projekt hatte nie ein `local`-Profil, nur `prod` vs. Default — kein neues Profil/Env-Var nötig für Dev-Komfort.
- **Kein Env-Var-Bootstrap-Key.** Erste Idee (Backend legt beim ersten Start automatisch einen SUPERUSER-Key aus einer Env-Var an) verworfen zugunsten eines einmaligen manuellen SQL-Inserts — einfacher, kein zusätzlicher Code-Pfad, da noch kein Cloud-Deployment existiert, das das rechtfertigen würde.
- **Kein `@SpringBootTest`-IT** (`SecurityConfigIT` aus dem P2-Prompt). Projekt hatte bislang keine `@SpringBootTest`/Testcontainers-Infrastruktur — Unit-Tests (Mockito) + `@WebMvcTest` decken die Filter-Logik und Endpoint-Regeln ausreichend ab, ohne neue Test-Infrastruktur einzuführen.

### P3 (Angular-UI) — Status: abgeschlossen (bis auf P4-Anbindung)

Umgesetzt: `AuthService`, Settings-Gating (Nutzer-Vorgabe: alles außer Font-Größe SUPERUSER-only), Zugriffsverwaltungs-UI inkl. Generieren/Bearbeiten/Sperren/Löschen, Rollen-Chip im Header (Gast/User/Superuser), GUEST-Gating der KI-Produktsuche (Feld/Buttons deaktiviert + Hinweistext statt aktivem, aber wirkungslosem UI).

**Nicht-offensichtliche Design-Punkte:**
- **Self-Lockout-Schutz:** `GET /api/auth/me` liefert zusätzlich `hasKey`/`accessKeyId`; die eigene Session wird in der Zugriffsverwaltung als „Du"-Badge markiert, Bearbeiten/Sperren/Löschen dafür deaktiviert — verhindert, dass sich ein Superuser selbst aussperrt.
- `LocalProfileAuthFilter` hat Vorrang vor `SessionCookieAuthFilter` (Kap. 2) — ein Key-Login während normalem Dev-Betrieb (`!prod`) ist dadurch wirkungslos. Settings zeigt das Zugriffscode-Feld in diesem Zustand deaktiviert mit Erklärung an, statt ein scheinbar funktionierendes Formular zu zeigen.

Offen: P4 (`AccessKeyUsage`-Kontingent-Zählung, Kap. 4/8) — Kontingent-Anzeige im Frontend baut darauf auf. Implementierungs-Prompt: `berechtigung-P4-kontingent-zaehlung.md` (2026-07-08: „eine AI-Anfrage" = nur Spec-Lookup, DL-Extraktion zählt nicht).

### P4 (Kontingent-Zählung) — Status: abgeschlossen (2026-07-08)

Ebene-2-Kontingent (Kap. 4) implementiert: Entity `AccessKeyUsage` + Migration `V44__create_access_key_usage.sql`, `AccessKeyUsageRepository` (native `findRemainingToday` + atomares `incrementToday`-Upsert per `ON CONFLICT`), `AccessKeyUsageService` (checkQuota/consume/remainingToday, `null` accessKeyId = SUPERUSER/GUEST → No-op), `QuotaExceededException`.

**Einhängung** (`ProductLookupService.lookup()`): `checkQuota` vor Cache-Check (propagiert bewusst an `ProductLookupController`, der auf `QUOTA_EXCEEDED` mappt — nicht im inneren try/catch gefangen); `consume` nur an den synchronen Erfolgspfaden (GOOD, bestes PARTIAL, PARTIAL trotz Rate-Limit). Cache-Hit, Provider-Erschöpfung und der asynchrone Rate-Limit-Retry buchen **nicht**. `accessKeyId` wird über `SecurityContextHolder`/`QuerCheckerPrincipal` aufgelöst (nur `role == USER` → echte Id, sonst `null`).

**Bewusste Abweichungen vom P4-Prompt (Nutzer-implizit / Vereinfachung):**
- **`AuthStatusDto` um `quotaRemaining` *und* `quotaLimit` erweitert** (Prompt nannte nur `quotaRemaining`). Beide nötig für die „X/Y heute"-Anzeige; nur der eigene Key, kein Info-Leak. Frontend: `AuthService.quotaUsed = quotaLimit - quotaRemaining`, Anzeige in Settings neben „Abmelden" (nur `role === 'USER'`).
- **Retention über `AuthProperties.usageRetentionDays` (Default 90) statt AppConfig-Key.** Konsistent mit der Schwester-Config `sessionDays`, die schon im selben `AuthProperties` liegt; der Cleanup läuft im bestehenden `UserSessionCleanupScheduler` (`deleteByPeriodDateBefore`) mit — kein neuer Cronjob.
- **QUOTA_EXCEEDED wird nicht per `ProductLookup` gecacht** — anders als das Provider-Kontingent, denn Ebene-2 ist pro User, ein globaler Cache-Eintrag würde alle sperren.

Tests: `AccessKeyUsageServiceTest` (Check unter/über/am Limit, Skip bei `null`, Increment, remaining), `ProductLookupServiceTest` erweitert (consume bei GOOD-Erfolg, kein consume bei Cache-Hit, `QuotaExceededException` propagiert vor Pipeline). Mockito/`@WebMvcTest`, kein `@SpringBootTest` (Konvention aus P2).

---

## Kapitel 9 — Bezug zu bestehendem Konzept

Ergänzt/erweitert `dl-spec-lookup-konzept.md`, Kap. 14 („Multi-User & Multi-Tenancy — für später"):

- Userverwaltung, Rollen → Kapitel 2/3 hier
- `ApiUsageLog` um `accessKeyId` erweitern → bleibt wie dort vorgesehen (Ebene 1, Kap. 4)
- Limits pro User-Key → präzisiert als zweistufiges Modell (Kap. 4)
