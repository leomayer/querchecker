# Querchecker — Konzept: Provider-Konfiguration & Graceful Startup

---

## Was kann ich ohne Konfiguration tun?

Querchecker ist auch ohne konfigurierte Provider nutzbar — als reiner Willhaben-Suchklient.
Technik-Details und Textanalyse-Engine benötigen externe KI-Services und sind ohne Konfiguration nicht verfügbar.

|                             | Willhaben-Suche | Textanalyse-Engine | Technik-Details |
| --------------------------- | --------------- | ------------------ | --------------- |
| Kein Provider konfiguriert  | ✅              | ❌                 | ❌              |
| Nur Web Search konfiguriert | ✅              | ❌                 | ❌              |
| Nur externe KI konfiguriert | ✅              | ✅                 | ❌              |
| Alles konfiguriert          | ✅              | ✅                 | ✅              |

**Hinweis:** Externe KI ist kritischer — ohne externe KI fällt auch die Textanalyse-Engine weg.
Technik-Details benötigen zwingend beide Services.

---

## Ziel dieses Features

Querchecker soll beim Start immer funktionieren — unabhängig davon ob externe Provider
konfiguriert sind oder nicht. Die App erkennt fehlende oder fehlerhafte Konfiguration
automatisch, kommuniziert dies klar im UI und bietet einen geführten Einrichtungs-Assistenten.

Dabei gilt:

- Kein Blocking beim Start
- Kein Neustart für den normalen Betrieb nötig
- Konfiguration erfolgt über `secrets.yml` — kein Speichern von Keys in der DB
- Provider-Status wird zentral via SSE verwaltet und ist im gesamten UI konsistent

---

## Provider-Dimensionen

### Dimension 1: Web Search

| Provider         | `active-provider`                     | Bemerkung                          |
| ---------------- | ------------------------------------- | ---------------------------------- |
| Brave Search     | `BRAVE`                               | produktiv, primär                  |
| Google Discovery | `GOOGLE_DISCOVERY`                    | in Implementierung/Test — siehe O2 |
| Keiner           | implizit (kein Key / Platzhalter-Key) | Spec-Lookup deaktiviert            |

### Dimension 2: Externe KI (Textanalyse-Engine)

| Provider   | `active-provider`                     | Bemerkung                                        |
| ---------- | ------------------------------------- | ------------------------------------------------ |
| Groq       | `GROQ`                                | primär, Free Tier                                |
| OpenRouter | `OPENROUTER`                          | noch nicht getestet — siehe O1                   |
| Lokal      | `LOCAL`                               | DJL-basiert (bestehende Implementierung)         |
| Keiner     | implizit (kein Key / Platzhalter-Key) | Technik-Details + Textanalyse-Engine deaktiviert |

**Ziel:** Aktiver Provider soll zur Laufzeit wechselbar sein via `AppConfig` (DB) — kein Neustart nötig.
`WebSearchProviderRouter` liest aktiven Provider dann aus DB (Implementierung → O15).
**Aktuell:** `WebSearchProviderRouter` liest aus statischer `SearchProperties` (application.yml) — Wechsel erfordert Neustart.

---

## Platzhalter-Key-Konzept & YAML-Export

Ein Provider gilt als `UNCONFIGURED` wenn:

- Key fehlt oder leer ist (`null`, `""`, Whitespace-only)
- Key dem Backend-definierten Platzhalter-Key entspricht (exakter Vergleich)

Backend definiert pro Provider einen kanonischen Platzhalter-Key. Die `example.yml`
enthält ausschließlich diese Platzhalter-Keys inkl. kurzer Kommentare — sie dient
als Template für den Export und ist damit immer synchron (kein Synchronisationsrisiko).

Prüfung im `ProviderStatusService`:

```java
private boolean isUnconfigured(String key, String placeholder) {
    return key == null || key.isBlank() || key.equals(placeholder);
}
```

**Sonderfall Google Discovery:** Kein API-Key — Authentifizierung über `credentialsPath`
(Pfad zur credentials-JSON-Datei). `UNCONFIGURED`-Prüfung analog: `credentialsPath`
fehlt/leer/Platzhalter. Ob die Datei existiert oder gültig ist → Lazy Validation
(erster Call → `UNAVAILABLE` bei Exception).

### `example.yml` als Template

Die `example.yml` wird beim Export als Template verwendet:

- Kommentare + Struktur bleiben erhalten
- Backend befüllt nur die Key-Werte
- Kein Hardcoding von Feldern oder Kommentaren im Backend/Frontend
- Neuer Provider → nur `example.yml` anpassen, kein Code-Change nötig

Der Einrichtungs-Assistent liest Felder + Kommentare aus der `example.yml` via
`GET /api/provider-setup/init` (erweitert um Template-Daten) → Eingabefelder
werden dynamisch aus der YAML-Struktur abgeleitet.

### Leere / nicht verwendete Keys

Wenn ein Provider nicht konfiguriert wird (z.B. Groq wenn OpenRouter aktiv):
→ **Option A (gewählt):** Platzhalter-Key einsetzen — `groq.api-key: GROQ_PLACEHOLDER`

Vorteile:

- Backend erkennt `UNCONFIGURED` korrekt
- `secrets.yml` bleibt vollständig (alle Felder vorhanden)
- `SKIPPED`-Flow im Assistenten setzt Platzhalter automatisch
- Konsistent mit dem bestehenden Platzhalter-Konzept

### Zwei-Dateien-Modell & Scope des Assistenten

Querchecker trennt Konfiguration strikt in zwei Dateien:

| Datei             | Inhalt                                   | In Git        | Assistent                       |
| ----------------- | ---------------------------------------- | ------------- | ------------------------------- |
| `secrets.yml`     | API-Keys                                 | ❌ gitignored | ✅ generiert Keys               |
| `querchecker.yml` | active-provider, Modell-Namen, Limits, … | ✅            | ✅ überschreibt Provider-Felder |
| `application.yml` | Framework/Infra (Port, DB, Flyway, …)    | ✅            | ❌ nie angefasst                |

**Credentials** landen ausschließlich in `secrets.yml`. `querchecker.yml` bleibt
credential-frei und ist sicher in Git commitbar.

Der Assistent generiert am Ende **beide Dateien** zum Download:

- `secrets.yml` — nur Keys; gitignored; nie commiten
- `querchecker.yml` — vollständig gemergt: bestehende Konfig + gewählter `active-provider`
  - `external-provider` + `model` + ggf. `credentials-path`. Backend rekonstruiert aus
    geladener Spring-Konfiguration, überschreibt nur die vom Assistenten gesetzten Felder.
    Direkt als Ersatz für die bestehende Datei verwendbar.

---

## Provider-Status: Zustände und Übergänge

### Backend-Zustände (5)

| Status         | Wann       | Bedeutung                                                        |
| -------------- | ---------- | ---------------------------------------------------------------- |
| `UNCONFIGURED` | beim Start | Key fehlt, leer, Whitespace oder == Platzhalter                  |
| `CONFIGURED`   | beim Start | Key syntaktisch vorhanden — inhaltliche Qualität unbekannt       |
| `VALID`        | nach Call  | Call erfolgreich                                                 |
| `UNREACHABLE`  | nach Call  | Temporärer Fehler (503, Timeout, Netzwerk)                       |
| `UNAVAILABLE`  | nach Call  | Permanenter Fehler (401/403) — braucht YAML-Korrektur + Neustart |

**Übergänge (Laufzeit):**

```
CONFIGURED  ──→  VALID
CONFIGURED  ──→  UNREACHABLE
CONFIGURED  ──→  UNAVAILABLE
VALID       ←→   UNREACHABLE
VALID       ──→  UNAVAILABLE
UNREACHABLE ──→  UNAVAILABLE
```

Server-Neustart setzt Status immer auf `UNCONFIGURED` oder `CONFIGURED` zurück —
unabhängig vom vorherigen Zustand. Neustart ist kein Übergang sondern ein Reset.

**Wichtig:**

- `UNCONFIGURED` und `CONFIGURED` entstehen **nur beim Start** (statische Prüfung)
- `VALID`, `UNREACHABLE`, `UNAVAILABLE` entstehen **erst nach einem echten API-Call**
- `CONFIGURED` = "syntaktisch vorhanden" — Key könnte ungültig sein oder auf falschen Pfad zeigen
- Rate Limits (429) → kein `UNREACHABLE`/`UNAVAILABLE` — laufen über Quota-Mechanismus

### Frontend-Zustände

Frontend kombiniert Backend-Status mit localStorage-Hash.
"Silent UNCONFIGURED" existiert nur im Frontend — keine Backend-Synchronisation nötig.

| Backend-Status | localStorage-Hash  | Popup      | Chip    | Technik-Details-Button | Inputfeld |
| -------------- | ------------------ | ---------- | ------- | ---------------------- | --------- |
| `UNCONFIGURED` | nicht bestätigt    | ✅ Warning | Warning | ausgegraut             | disabled  |
| `UNCONFIGURED` | bestätigt (silent) | —          | Warning | ausgegraut             | disabled  |
| `CONFIGURED`   | —                  | —          | Warning | aktiv                  | aktiv     |
| `VALID`        | —                  | —          | —       | aktiv                  | aktiv     |
| `UNREACHABLE`  | —                  | —          | Error   | aktiv                  | aktiv     |
| `UNAVAILABLE`  | —                  | —          | Error   | ausgegraut             | disabled  |

---

## ProviderStatus via SSE

Der `ProviderStatus` wird über den **bestehenden SSE-Kanal** transportiert.

**Beim Connect:** Backend sendet sofort ein `provider-status`-Event (zusätzlich
zum bestehenden Timestamp) → `ProviderStatusStore` wird befüllt.

**Bei Statusänderung:** Backend sendet `provider-status`-Event nur bei tatsächlicher
Änderung (Lazy Validation → `UNREACHABLE`/`UNAVAILABLE`, Test-Button → `VALID`).

**`ProviderStatusStore` ist read-only im Frontend:**

- Befüllt ausschließlich durch SSE-Events vom Backend
- Status kann nur vom Backend gesetzt werden
- Frontend löst Aktionen aus (Test-Button, Technik-Details) die zu Statusänderungen führen
- Einzige frontend-seitige Ausnahme: localStorage-Hash für Popup-Acknowledgement

---

## `ProviderStatus` — Backend-Modell

**Hinweis:** `SseHub.startToken` (bereits implementiert) dient als `serverStartToken` — wird
bereits beim `server-hello` Event gesendet. Das `provider-status`-Event ist implementiert
(`ProviderStatusService.broadcastStatus()` → `sseHub.broadcast("provider-status", getStatus())`),
aber noch **nicht end-to-end getestet** — Frontend-Empfang und Store-Befüllung ausstehend.

```java
public record ProviderStatus(
    ProviderState searchState,   // UNCONFIGURED | CONFIGURED | VALID | UNREACHABLE | UNAVAILABLE
    ProviderState llmState,
    String searchProvider,       // "BRAVE" | "GOOGLE_DISCOVERY" | null
    String llmProvider,          // "GROQ" | "OPENROUTER" | "LOCAL" | null
    String searchError,          // null oder Fehlergrund (nur bei UNREACHABLE/UNAVAILABLE)
    String llmError,
    Integer searchHttpStatus,    // null oder HTTP-Status (nur bei UNREACHABLE/UNAVAILABLE)
    Integer llmHttpStatus,
    String serverStartToken      // = SseHub.startToken (bereits vorhanden)
) {}
```

---

## API-Endpoints

**`GET /api/provider-status`** — aktueller Status inkl. `serverStartToken`.
Wird via SSE gepusht; REST-Endpoint für direkten Abruf ebenfalls verfügbar.

**`GET /api/provider-setup/init`** — nur aufrufbar wenn mind. ein Provider nicht `VALID`.
Liefert Platzhalter-Keys + Backend-Pfade für beide Konfig-Dateien:

```json
{
  "placeholderKeys": { "brave": "BRAVE_PLACEHOLDER", "groq": "GROQ_PLACEHOLDER", ... },
  "secretYmlPath": "config/secrets.yml",
  "quercheckerYmlPath": "config/querchecker.yml"
}
```

**`GET /api/provider-setup/keys?provider=BRAVE`** — lazy pro Flow.
Nur abrufbar wenn Provider `CONFIGURED`, `UNREACHABLE` oder `UNAVAILABLE`.
Gibt bestehenden Key zurück, oder `null` wenn Platzhalter-Key erkannt.
Keys kommen aus geladenen Spring-Properties — kein extra Filesystem-Zugriff.
Auth-geschützt (Single-User: HTTP Basic Auth reicht; Multi-User: Berechtigung binden).

**Sonderfall `GOOGLE_DISCOVERY`:** Endpoint gibt statt eines Keys den `credentialsPath`
zurück + ob die Datei unter diesem Pfad gefunden wurde:

```json
{ "credentialsPath": "/etc/querchecker/google-credentials.json", "credentialsFileFound": true }
```

Gibt dem User sofortiges Feedback im Assistenten — kein Warten auf den ersten echten API-Call.
Sicherheitsrisiko vertretbar (Filesystem-Info bleibt hinter Auth-geschütztem Endpoint).

**`POST /api/provider-setup/save`** — Bonus: schreibt beide Dateien direkt auf den Server.
Auth-geschützt. Schlägt fehl wenn Filesystem-Berechtigung fehlt (z.B. `:ro`-Mount in Prod)
→ Frontend zeigt Download-Fallback.

**`POST /api/admin/restart`** — sendet SIGTERM an den eigenen JVM-Prozess (Shutdown).
**Kein eigentlicher Neustart** — der Prozess beendet sich; der tatsächliche Neustart obliegt
dem Prozess-Manager (Docker restart-policy, systemd `Restart=always`, o.ä.).
Ohne Prozess-Manager bleibt der Server nach dem Shutdown down.
SSE erkennt Neustart → normaler Reconnect-Flow mit Snackbar.

---

## Provider-agnostisches Fehler-Handling

✅ **Implementiert** (`at.querchecker.api.result.ApiCallResult`). `RateLimitException` existiert
noch für externe Callers (`unwrapOrThrow` wirft sie), aber `AbstractLlmExtractionClient.callLlm()`
arbeitet intern bereits mit `ApiCallResult`.

Provider-spezifische Rate-Limit-Header (zur Referenz):

| Provider         | Rate Limit | Retry-After            | Bemerkung                                                       |
| ---------------- | ---------- | ---------------------- | --------------------------------------------------------------- |
| Groq             | 429        | `Retry-After` Header   | implementiert                                                   |
| OpenRouter       | 429        | `X-RateLimit-*` Header | abweichendes Format — aktuell wie Groq behandelt (Fallback 60s) |
| Ollama (LOCAL)   | keins      | —                      | lokal, kein Limit                                               |
| Cerebras         | unbekannt  | unbekannt              | noch nicht getestet                                             |
| Google Discovery | unbekannt  | unbekannt              | generisches `catch` vorhanden                                   |

Dasselbe Prinzip gilt noch für `WebSearchService` (offen).
Fallback wenn kein `Retry-After` geliefert wird: konfigurierbares Default in `application.yml` (Status unbekannt).

---

## Validation: Lazy

Kein aktiver Ping beim Start — keine unnötigen API-Calls.

**Beim Start:** Nur statische Konfig-Prüfung → `UNCONFIGURED` oder `CONFIGURED`

**Lazy (erster echter Call):**

- `Unreachable` (503, Timeout) → Status `UNREACHABLE` → Button bleibt aktiv
- `Unavailable` (401/403) → Status `UNAVAILABLE` → Button ausgegraut
- Beide: Backend markiert Provider + speichert Fehlergrund + HTTP-Status → SSE-Event

**Manuell:** Test-Button in Settings → nur für Remote-Provider, nur bei `CONFIGURED`,
`UNREACHABLE` oder `UNAVAILABLE`

---

## Startup-Flow (Beschreibung)

**Beim Start / F5:**
Backend führt statische Konfig-Prüfung durch → `UNCONFIGURED` oder `CONFIGURED`.
Nach SSE-Connect sendet Backend sofort ein `provider-status`-Event → `ProviderStatusStore` befüllt.

- `UNCONFIGURED` (mind. 1) → `acknowledgedHash` aus localStorage prüfen:
  nicht vorhanden oder veraltet → Popup + Badge Warning; vorhanden → nur Badge Warning (silent)
- `CONFIGURED` (alle) → Badge Warning, kein Popup, Spec-Lookup-Button aktiv

**Laufzeit:**
Erster Call schlägt fehl → SSE-Event mit neuem Status:

- `UNREACHABLE` → Badge Error, Button bleibt aktiv
- `UNAVAILABLE` → Badge Error, Button ausgegraut
- `VALID` → Badge verschwindet

---

## UNCONFIGURED: Popup + Badge

**Popup** erscheint nur bei `UNCONFIGURED` — nie bei `CONFIGURED`, `UNREACHABLE` oder `UNAVAILABLE`.

### Hash-basiertes Acknowledgement

```
acknowledgedHash = serverStartToken + searchProvider + llmProvider + searchState + llmState
```

Die 5 Zustände (`UNCONFIGURED`, `CONFIGURED`, `VALID`, `UNREACHABLE`, `UNAVAILABLE`)
erzeugen jeweils unterschiedliche Hashes — `UNREACHABLE` und `UNAVAILABLE` können
beim Neustart nicht vorkommen (immer Reset auf `UNCONFIGURED`/`CONFIGURED`).

- Hash nicht im localStorage oder Hash != aktueller Hash → Popup zeigen
- Nach „Trotzdem fortfahren" → Hash speichern → silent bis nächster Neustart
- Jeder Neustart → neues `serverStartToken` → neuer Hash → Popup ✅
- F5 gleiche Instanz → gleicher Hash → kein erneutes Popup ✅

### Popup-Inhalt

```
⚠️ Einige Features sind nicht eingerichtet

Querchecker benötigt externe Provider für Spec-Lookup und DL-Extraktion.
Die Konfiguration erfolgt über eine secrets.yml die du nach der Einrichtung
herunterlädst und im Backend-Verzeichnis ablegst.
Danach ist ein Server-Neustart erforderlich.

🔴 Web Search    — nicht konfiguriert
✅ LLM Provider  — Groq (konfiguriert)

Ohne Web Search ist Spec-Lookup nicht verfügbar.

[ Einrichten ]  [ Trotzdem fortfahren ]
```

„Einrichten" navigiert zur Settings/Provider-Route — Popup schließt sich.

### Badge auf Settings-Button (Header)

| Zustand                                    | Badge      | Badge-Farbe |
| ------------------------------------------ | ---------- | ----------- |
| mind. 1 × `UNCONFIGURED` oder `CONFIGURED` | sichtbar   | Warning     |
| mind. 1 × `UNREACHABLE` oder `UNAVAILABLE` | sichtbar   | Error       |
| alle `VALID`                               | unsichtbar | —           |

`Warning` und `Error` sind Badge-Farben — keine eigenen Zustände.
Klick → Settings öffnet direkt auf Provider-Konfig-Tab.

> **TODO vor Implementierung:** Badge-Farben mit bestehenden Warning-/Error-Farben
> abgleichen (Usage Monitor, GradientProgressBar, ⚠️-Icons).

---

## Einrichtungs-Assistent: YAML-Generator

Rein clientseitiger YAML-Generator — Keys werden nie in der DB gespeichert.
Keys verlassen den Browser nur durch bewussten Download/Copy.

### Setup-Store (Frontend, session-weit)

```
SetupStore
    ├── webSearch: { provider, key, status: IDLE | EDITING | DONE | SKIPPED }
    └── llm:       { provider, key/path, status: IDLE | EDITING | DONE | SKIPPED }
```

- Initialisiert beim ersten Öffnen — kein sofortiger Backend-Call
- Keys werden lazy pro Flow geholt wenn Provider nicht `UNCONFIGURED`:
  `GET /api/provider-setup/keys?provider=X` → `null` wenn Platzhalter erkannt
- Hin- und Herswitchen zwischen Flows ohne Datenverlust
- Download nochmals möglich solange Tab offen ist
- Bei Tab-Close oder F5 → Store geleert
- `UNREACHABLE`/`UNAVAILABLE`: Key wird geladen (User kann korrigieren)

`SKIPPED` = Provider bewusst nicht konfiguriert → **bestehender Wert vom Backend beibehalten** (falls vorhanden);
nur wenn kein Wert vorhanden ist → Platzhalter-Key einsetzen. Gilt auch für `credentialsPath`.
`DONE` = Key eingetragen → in `secrets.yml`.
Generieren-Button aktiv wenn beide Flows `DONE` oder `SKIPPED`.

**Server-Neustart während offenem Assistenten:**
`ProviderStatusStore` wird via SSE automatisch aktualisiert.
`SetupStore` muss nicht zurückgesetzt werden — User sieht aktualisierten Status am Einstiegsscreen.

### Frontend-Lock: Advisory via localStorage

```
localStorage["setup-lock"] = { timestamp }
```

**Tab B öffnet Assistenten, Lock vorhanden:**

```
„Setup bereits in einem anderen Fenster aktiv"
[ Setup übernehmen ]  [ Nur anschauen ]
```

- **„Setup übernehmen"** → Lock mit neuem Timestamp überschreiben → Tab B ist neuer Setup-Client
- **„Nur anschauen"** → Read-only, keine Eingaben, kein Download
- **Tab A merkt Lock-Verlust** beim nächsten Schreib-Versuch → Read-only
- **Lock-Freigabe:** Dialog geschlossen / Abbrechen → Lock aus localStorage entfernen
- Tab geschlossen → `beforeunload` entfernt Lock

### Einstiegsscreen

Zeigt immer aktuellen Status beider Dimensionen aus `ProviderStatusStore`.
`GET /api/provider-setup/init` nur wenn mind. ein Provider nicht `VALID`.
Bei vollem `VALID`: nur Statusübersicht, keine Buttons.

```
┌─────────────────────────────────────────────┐
│ Provider einrichten                          │
│                                              │
│ Querchecker benötigt externe Provider für    │
│ Spec-Lookup und DL-Extraktion. Die           │
│ Konfiguration erfolgt über eine secrets.yml   │
│ die du nach der Einrichtung herunterlädst    │
│ und im Backend-Verzeichnis ablegst.          │
│ Danach ist ein Server-Neustart erforderlich. │
│                                              │
│ Aktueller Status:                            │
│ 🔴 Web Search   — nicht konfiguriert        │← UNCONFIGURED: Button sichtbar
│ ✅ LLM Provider — Groq (gültig)             │← VALID: kein Button
│                                              │
│ [ Web Search einrichten ]                    │← nur für nicht-VALID Provider
│ [ Abbrechen             ]                    │
└─────────────────────────────────────────────┘
```

Bei vollem `VALID` (beide Provider): nur Statusanzeige, keine Aktions-Buttons, nur Schließen.

### Ablauf pro Flow

| Ausgangszustand                              | Eingabefeld                     | Buttons                        |
| -------------------------------------------- | ------------------------------- | ------------------------------ |
| `UNCONFIGURED`                               | leer                            | [ Speichern ] [ Überspringen ] |
| `CONFIGURED` / `UNREACHABLE` / `UNAVAILABLE` | vorausgefüllt (Key vom Backend) | [ Speichern ]                  |
| `VALID`                                      | vorausgefüllt (read-only)       | —                              |

1. Provider wählen (Web Search: Brave / Google Discovery; LLM: Groq / OpenRouter / LOCAL)
2. Erklärender Text + Link zur Provider-Seite
3. Eingabefeld(er) je nach Ausgangszustand (siehe Tabelle oben)
   - Modell-Name: **Freitext-Feld** — User kopiert Namen aus Provider-Dokumentation
   - Hinweis: _„Das konfigurierte Modell wird erst nach dem Server-Neustart getestet"_
4. Für LOCAL: Verweis auf Kurzanleitung im Repository
5. Für Google Discovery: Pfad zur credentials-JSON-Datei + Hinweis Ablageort (vom Backend)
6. Flow abschließen → `DONE` oder `SKIPPED` → zurück zum Einstiegsscreen

### Generierung + Ausgabe

Sobald beide Flows `DONE` oder `SKIPPED`:

```
✅ Konfiguration abgeschlossen

Schritt 1 — API-Keys speichern (niemals in Git):
  Speichere als: {secretYmlPath}
  [ secrets.yml herunterladen ]  [ In Zwischenablage kopieren ]

Schritt 2 — Provider-Konfiguration speichern (in Git commitbar):
  Speichere als: {quercheckerYmlPath}  ← ersetzt deine bestehende Datei
  [ querchecker.yml herunterladen ]

Schritt 3 — Server neu starten:
  [ Server neu starten ]
```

`secretYmlPath` und `quercheckerYmlPath` kommen beide vom Backend via
`GET /api/provider-setup/init` — Backend kennt seine eigenen Konfig-Pfade.

**`querchecker.yml`-Generierung:** Backend liefert die geladene Konfiguration mit den im
Assistenten gewählten Werten überschrieben (`active-provider`, `external-provider`, `model`,
`credentials-path`). Alle anderen Felder (Limits, Timeouts, Crons, …) bleiben unverändert.
Kein Credentials-Inhalt — sicher in Git commitbar.

**YAML-Quoting:** String-Werte in der generierten `secrets.yml` werden **mit einfachen Anführungszeichen**
ausgegeben (z.B. `groq-api-key: 'gsk_...'`). Verhindert Parsing-Fehler bei Sonderzeichen (`:`, `#`, `*`, …).

Nach „Server neu starten": `POST /api/admin/restart`
→ SSE erkennt Neustart → normaler Reconnect-Flow mit Snackbar.

### Bonus: Direkt auf Server speichern

Statt Download + manuelles Ablegen kann der Assistent die Dateien auch direkt via
`POST /api/provider-setup/save` auf den Server schreiben — Backend übernimmt das Ablegen
an den bekannten Pfaden. Vereinfacht den Flow auf einen Klick: „Speichern & Neu starten".

**Kein Secret-Breach:** Keys reisen über dieselbe HTTP-Verbindung wie alle anderen Requests
(HTTPS in Prod, verschlüsselt). Backend schreibt sie auf Disk — funktional identisch mit
manuellem Editieren per SSH. Voraussetzung: Endpoint ist auth-geschützt (wie `/keys`).

**Abhängig von Filesystem-Permissions:**

- Dev (lokal): funktioniert in der Regel ohne Anpassung
- Prod (Docker): `docker-compose.prod.yml` mountet `secrets.yml` aktuell als `:ro` →
  Mount müsste auf `:rw` geändert werden, damit das Backend schreiben kann
- Backend prüft Schreibbarkeit und zeigt Download-Fallback wenn nicht möglich:
  _„Direktes Speichern nicht verfügbar — Dateisystem-Berechtigung fehlt"_

### Einstiegspunkte

- Popup → „Einrichten"
- Settings/Provider → **jederzeit**, unabhängig vom Status (auch `VALID` zur Statusübersicht)
- Badge auf Settings-Button → Klick → Settings/Provider

---

## Aktionen in Settings/Provider pro Zustand

| Zustand        | Test-Button (nur Remote) | Assistent             |
| -------------- | ------------------------ | --------------------- |
| `UNCONFIGURED` | —                        | ✅ einrichten         |
| `CONFIGURED`   | ✅                       | ✅ neu konfigurieren  |
| `VALID`        | —                        | ✅ nur Status-Anzeige |
| `UNREACHABLE`  | ✅                       | ✅ neu konfigurieren  |
| `UNAVAILABLE`  | ✅                       | ✅ neu konfigurieren  |

LOCAL: kein Test-Button — DJL wirft beim Start Exception wenn fehlerhaft →
`UNAVAILABLE` + Fehlermeldung in Settings: _„Lokales Modell [name] konnte nicht
gestartet werden"_ + Link zur Kurzanleitung.

---

## Szenarien in `item-research`

### Szenario 1: `UNCONFIGURED` (ganz oder teilweise)

- Inputfeld disabled, Button ausgegraut
- Hinweis je nach Situation:
  - Alles fehlt: _„Spec-Lookup nicht verfügbar — Web Search und LLM nicht konfiguriert"_
  - Nur Web Search fehlt: _„Spec-Lookup nicht verfügbar — Web Search nicht konfiguriert"_
  - Nur LLM fehlt: _„Spec-Lookup und DL-Extraktion nicht verfügbar — LLM nicht konfiguriert"_
- Immer: + Link zu Settings
- Braucht YAML-Änderung + Neustart

### Szenario 2: Alles `CONFIGURED`

- Inputfeld aktiv, Button aktiv, kein Hinweis
- Erster Klick → Lazy Validation → `VALID`, `UNREACHABLE` oder `UNAVAILABLE`

### Szenario 3: `UNREACHABLE` (503, Timeout)

- Fehlermeldung unter Inputfeld: welcher Provider + Fehlergrund
- Button bleibt aktiv → User klickt nochmals
- Badge Error sichtbar

### Szenario 4: `UNAVAILABLE` (401/403)

- Button ausgegraut, Inputfeld disabled
- Fehlermeldung: HTTP-Status + Link zu Settings
- Braucht YAML-Korrektur + Neustart

### Szenario 5: Rate Limit (429)

- Über Quota-Mechanismus (`QUOTA_EXCEEDED`) — kein `UNREACHABLE`/`UNAVAILABLE`
- Provider-agnostisch via `ExtractionResult.RateLimited`

---

## Degradation im UI (Übersicht)

| Zustand                  | Spec-Lookup-Button   | DL-Extraktion         | Badge   | Inputfeld |
| ------------------------ | -------------------- | --------------------- | ------- | --------- |
| `UNCONFIGURED` (mind. 1) | ausgegraut + Tooltip | ausgegraut (wenn LLM) | Warning | disabled  |
| `CONFIGURED` (alle)      | aktiv                | aktiv                 | Warning | aktiv     |
| `VALID` (alle)           | aktiv                | aktiv                 | —       | aktiv     |
| `UNREACHABLE` (mind. 1)  | aktiv                | aktiv (wenn LLM)      | Error   | aktiv     |
| `UNAVAILABLE` (mind. 1)  | ausgegraut + Tooltip | ausgegraut (wenn LLM) | Error   | disabled  |

---

## Migration: ConfigController ablösen (Schritt 11a)

Im Zuge dieser Implementierung wird der veraltete `ConfigController` vollständig entfernt
und durch `GET /api/provider-status` ersetzt.

### IST-Zustand

- **Backend:** `ConfigController` liefert `GET /api/config/providers` (`API_URLS.configProviders`)
  mit `keyPresent`, `limitsConfigured`, `active` je Provider — fachlich überholt durch `ProviderStatus`
- **Frontend:** Generierter `ConfigService` (`config.service.ts`) ist **nicht in Verwendung** —
  kein Feature-Komponent konsumiert ihn
- **`ItemResearchComponent.aiSearchEnabled`:** `input(true)` — Stub, nie verdrahtet, immer aktiv

### Umsetzung

**Backend:**

- `ConfigController` löschen
- Kein Breaking Change: `ConfigService` hat keine aktiven Konsumenten

**Frontend:**

- Generierten `ConfigService` löschen (inkl. `API_URLS.configProviders`)
- `aiSearchEnabled` in `ItemResearchComponent` von `input(true)` auf ein computed Signal umstellen,
  das aus dem `ProviderStatusStore` liest:

```typescript
// Vorher
aiSearchEnabled = input(true);

// Nachher
aiSearchEnabled = computed(() => {
  const s = this.providerStatusStore.status();
  if (!s) return false;
  const ok = (state: ProviderState) => state === 'CONFIGURED' || state === 'VALID';
  return ok(s.searchState) && ok(s.llmState);
});
```

`UNCONFIGURED` oder `UNAVAILABLE` bei einem der beiden Provider → `false` →
`item-research` Degradation greift (Szenario 1 / 4).

### Abhängigkeit

Schritt 6 (ProviderStatusStore) muss vorher abgeschlossen sein.
Schritt 11 (item-research Degradation) kann parallel oder direkt danach erfolgen.

---

# OFFEN

---

## O1 — OpenRouter: Noch nicht getestet

- Funktioniert bestehende Implementierung mit echtem Key?
- Ist `meta-llama/llama-3.3-70b-instruct:free` noch verfügbar?
- Antwortformat-Unterschiede zu Groq?
- Rate-Limit-Format für `ExtractionResult.RateLimited`-Mapping?

**Aktion:** Vor Implementierung praktisch testen.

---

## O2 — Google Discovery: Reifegrad unklar

`GoogleDiscoveryWebSearchService` ist vollständig implementiert (SDK, Dedup, Snippets,
Usage-Logging, `UNCONFIGURED`-Prüfung, `keys`-Endpoint, Fehler-Handling — alles geklärt
und im Dokument eingearbeitet).

**Offen:** Funktioniert die Integration mit echtem GCP-Projekt zuverlässig?
Gleichwertig zu Brave oder vorerst experimentell?

**Aktion:** Vor Implementierung praktisch testen.

---

# TODO: Kurzanleitung Lokale Modelle (im Repository)

Erstellen im Zusammenhang mit den Sourcen — nicht in diesem Dokument.

- Was muss installiert sein? (Native Libraries für DJL auf openSUSE Tumbleweed)
- Welche Modelle sind verfügbar und getestet?
  (`Llama32ExtractionModel`, `NuExtractExtractionModel`, etc.)
- Wie wird ein Modell beim ersten Start heruntergeladen?
  (Hugging Face automatisch via DJL — wo landet es lokal?)
- Wie wird das aktive Modell in `application.yml` konfiguriert?
- Wie aktiviert/deaktiviert man ein Modell?
- Was passiert wenn das Modell beim Start noch nicht vorhanden ist?
- Mögliche Fehlerursachen für Settings-Fehleranzeige
  (fehlende Native-Library, Speichermangel, Modell nicht vorhanden, …)

---

# IMPLEMENTIERUNGSREIHENFOLGE

## Voraussetzungen

- O1: OpenRouter praktisch testen (blockiert nur ApiCallResult-Mapping für OpenRouter)
- O2: Google Discovery Reifegrad bestimmen (kann parallel laufen)
- O3: ✅ Gelöst — Zwei-Dateien-Modell (secrets.yml + querchecker.yml)

## Blöcke

Die Schritte sind in testbare Blöcke gruppiert. Jeder Block kann einzeln beauftragt
werden (z.B. `"Starte Block A"`). Für Backend-Blöcke Skill `work-backend` laden,
für Frontend-Blöcke `work-frontend`.

---

### Block A — Backend Core

**Refactoring:**

- ✅ `ApiCallResult` (Sealed Interface) implementiert — `api/result/ApiCallResult.java`
- ✅ `ExtractionClient` + `AbstractLlmExtractionClient` arbeiten intern mit `ApiCallResult`
- ⚠️ `RateLimitException` noch nicht vollständig ersetzt — externe Callers werfen sie noch
- ⚠️ `WebSearchService` (`GoogleDiscoveryWebSearchService`) noch nicht auf `ApiCallResult` umgestellt
- ❓ Fallback `retry-after-default-seconds` in `application.yml` — Status unbekannt

**Implementierung:**

1. `example.yml` als Template definieren (Kommentare + Platzhalter-Keys)
2. `ProviderStatusService`: `isUnconfigured`-Prüfung (inkl. Sonderfall Google Discovery: `credentialsPath`)
3. `ProviderStatusService`: statische Konfig-Prüfung beim Start → `UNCONFIGURED` / `CONFIGURED`
4. SSE: `provider-status`-Event beim Connect + bei Statusänderung
5. Endpoints: `GET /api/provider-status`, `GET /api/provider-setup/init`, `GET /api/provider-setup/keys`
   (inkl. GOOGLE_DISCOVERY: gibt `credentialsPath` + `credentialsFileFound` zurück), `POST /api/admin/restart`

**JUnit-Tests:**

- `ProviderStatusServiceTest`: `isUnconfigured` für alle Fälle (null, leer, Platzhalter, echter Key; credentialsPath-Varianten)
- `ProviderStatusServiceTest`: statische Prüfung beim Start liefert korrekten Status
- `ApiCallResultTest`: sealed interface + Mapping von HTTP-Status auf korrekten Typ

**Testbar nach Block A:** `GET /api/provider-status` im Browser/Postman; SSE-Event beim Connect im DevTools-Netzwerk-Tab

---

### Block B — Frontend Status

6. `ProviderStatusStore` (read-only, SSE-befüllt)
7. Popup (hash-basiertes Acknowledgement) + Badge auf Settings-Button
8. Status-Anzeige + Aktionen in Settings/Provider (Test-Button-Platzhalter, Assistent-Button)

**JUnit-Tests:** (Frontend: keine Unit-Tests üblich — manuelles Testen ausreichend)

**Testbar nach Block B:** Badge + Popup erscheinen/verschwinden je nach simuliertem Status;
Settings zeigt korrekten Zustand pro Provider

---

### Block B1 — Wording

8.  a) Prinzipiell verwende kein Wording auf der UI das zu sehr ins Backend geht. Konkret sollte DL-Extraction nicht aufscheinen, weil einerseits Englisch, andererseits DL wenig Bedeutung hat - Extraction vielleicht noch eher. Vielleicht gibt's hierzu ein besseres Wording
    b) Spec-Lookup - einerseits ist der Begriff in der README als auch vermutlich hier oft genug verwendet worden. Es ist aber nur eine Kurzbezeichnung für den Teil, dass die (technische) Spezifikation eines Artikels nachgeschaut werden kann. Bitte das Wording entsprechend umgestalten
    c) Das Valdierungsergebnis in 3 bzw. 4 Tabellen-Spalten aufteilen, anstelle eines künstlich erzeugten <divs>, z.B.
    |Icon|Provider|Ausgewählte Provider|konfiguriert (als icon/Checkbox)|valdiert (als icon/checkbox)
    d) Auch wenn mir die Gestaltung vom provider-status-popup gut gefällt, sollte der Hinweis mit der Warnung "Ohne LLM Provider ist DL-Extraktion nicht verfügbar." prominenter ersichtlich sein. Oder einfacher rasch erkennbar, das etwas nicht weitergeht
    e) Bei der Gestaltung in der UI bitte die Farbkontraste mit 4.5 beibehalten. Zwar funktioniert die Übersicht der Einstellungen die Anzeige, jedoch sind die Farben `--color-tertiary` nur gut in dark-modus gut sichtbar
    f) die Badge ist im dunklen Modus nicht sichtbar - zumindest wenn sie auf "small" gesetzt ist. Prinzipiell ist das Styling zu einfach und kaum wahrnehmbar. Ausserdem ist der Circle-rundherum fast zu klein - da gehört mehr Padding dazu!

---

### Block C — Einrichtungs-Assistent

9. `SetupStore` + Frontend-Lock (localStorage)
10. Einrichtungs-Assistent: YAML-Generator, dynamische Felder aus `example.yml`-Template,
    SKIPPED → Platzhalter, Neustart-Button

**Testbar nach Block C:** YAML-Download prüfen (Keys korrekt befüllt, Platzhalter für SKIPPED);
Mehrfach-Tab-Lock-Verhalten

---

### Block C1 — Zwischen-Schritt

1. ✅ **Geklärt** — `SKIPPED`-Flow behält bestehende Backend-Werte; Platzhalter nur wenn kein Wert vorhanden. Gilt auch für `credentialsPath`. (→ SKIPPED-Logik + `SetupStore` aktualisiert)
2. ✅ **Geklärt** — String-Werte in `secrets.yml` mit einfachen Anführungszeichen ausgeben. (→ YAML-Quoting-Abschnitt ergänzt)
3. ✅ **Geklärt** — `POST /api/admin/restart` ist ein reiner Shutdown (SIGTERM). Neustart erfolgt durch externen Prozess-Manager (Docker restart-policy, systemd). Ohne Prozess-Manager bleibt der Server down. (→ Endpoint-Beschreibung präzisiert)
4. ✅ **Geklärt** Wie kann ich testen, ob bzw. was beim Download verfügbar ist?

```
chmod -w backend/config/
→ Backend liefert `serverWritable: false` → Frontend zeigt Download-Button → Dateien herunterladen + prüfen.

Danach:

chmod +w backend/config/
```

5. ✅ **Geklärt** Ist eigentlich geklärt, ob die Einrichtung der Konfig nun so funktioniert, dass sämtliche relevanten Infos gespeichert werden?
6. ✅ **Geklärt** Bei den Google-Credentials Path ist mir unklar, wo dieser "beheimatet" sein soll. Einerseits wird er über den Assistenten angeboten, andererseits ist er im `querchecker.yml` enthalten

```
Kein Widerspruch — zwei verschiedene Dinge:

- **`credentials-path`** (z.B. `/etc/querchecker/google-credentials.json`) ist kein Secret, sondern nur ein Dateisystempfad → landet in **`querchecker.yml`** (git-safe, kein Credentials-Inhalt)
- Die eigentliche **`google-credentials.json`-Datei** liegt auf dem Server an diesem Pfad — der User muss sie manuell dorthin legen (außerhalb des Wizard-Scopes)

Der Assistent zeigt den Pfad aus `GET /api/provider-setup/keys?provider=GOOGLE_DISCOVERY` an (inkl. `credentialsFileFound`), damit der User bestätigen oder korrigieren kann. Das Ergebnis schreibt der Wizard in `querchecker.yml`.
```

---

### ✅ Block D — Degradation + Migration

11. `item-research` Degradation (alle Szenarien: UNCONFIGURED, CONFIGURED, UNREACHABLE, UNAVAILABLE, Rate Limit)
    11a. `ConfigController` + generierten `ConfigService` löschen; `aiSearchEnabled` auf `ProviderStatusStore` umstellen

**JUnit-Tests:** (Backend entfällt — `ConfigController` wird gelöscht, kein neuer Code)

**Testbar nach Block D:** item-research mit absichtlich falschem/fehlendem Key; `aiSearchEnabled`-Logik

---

### Block E — Lazy Validation + Test-Button

12. Lazy Validation: erster Call schlägt fehl → `UNREACHABLE`/`UNAVAILABLE` via SSE
13. Test-Button in Settings (nur Remote-Provider, nur bei CONFIGURED/UNREACHABLE/UNAVAILABLE)

**JUnit-Tests:**

- `ProviderStatusServiceTest`: `ApiCallResult.Unreachable` → Status `UNREACHABLE` + SSE-Event
- `ProviderStatusServiceTest`: `ApiCallResult.Unavailable` → Status `UNAVAILABLE` + SSE-Event

**Testbar nach Block E:** absichtlich falschen Key setzen → UNAVAILABLE; Test-Button → VALID

---

### ✅ Block F — Cleanup + Laufzeit-Wechsel

14. ✅ LOCAL-Fehleranzeige in Settings + Link zur Kurzanleitung (`docs/local-models.md`)
15. `WebSearchProviderRouter` auf DB/`AppConfig` umstellen (Laufzeit-Wechsel ohne Neustart)

**Testbar nach Block F:** Provider zur Laufzeit wechseln ohne Neustart

---

### Parallel (unabhängig)

→ Kurzanleitung lokales LLM im Repository
