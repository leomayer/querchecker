# Querchecker — Gesamtkonzept Spec-Lookup & Item Research

---

## Übersicht: Gesamtflow

```
Inserat öffnen
    │
    ▼
DL-Extraktion (Groq)
→ Produktname als Vorschlag für lookupTerm
    │
    ▼
item-research: Suchfeld
→ vorausgefüllt mit lookupTerm oder DL-Vorschlag
→ User kann korrigieren
    │
    ▼
[Specs laden] geklickt
→ lookupTerm in WhListingDetail speichern
→ Cache-Check [Kap. 6]
→ Kontingent-Check [Kap. 7]
→ Brave Search [Kap. 8]
→ LLM-Verarbeitung [Kap. 9]
→ Ergebnis in UI anzeigen [Kap. 2]
    │
    ▼ (optional)
[Alle Specs laden]
→ Icecat-API direkt [Kap. 10]
    │
    ▼
Felder mit ⭐ markieren
→ Kategorie-Präferenzen [Kap. 3]
```

---

# NUTZER-PERSPEKTIVE

---

## Kapitel 1 — Grundprinzip

- Specs werden **explizit auf Nutzer-Anfrage** geladen
- Ergebnisse dauerhaft gecacht — Specs ändern sich nach Produktrelease nicht
- Mehrere Inserate mit gleichem `lookupTerm` teilen einen Cache-Eintrag
- `lookupTerm` wird immer in `WhListingDetail` gespeichert — auch bei Fehler
- Aktuell: **Single-User**, HTTP Basic Auth für Demo-Zugang

---

## Kapitel 2 — UI: item-research

### Layout

```
┌──────────────────────────────────────────┐
│ ITEM RESEARCH                             │
│                                           │
│ ┌───────────────────────┬──────────────┐ │
│ │ [lookupTerm      ✏️]  │ ⭐ CPU       │ │
│ │ [Laden] [↗ Geizhals]  │    Core U. 7 │ │
│ │                        │ ⭐ RAM       │ │
│ │                        │    16 GB     │ │
│ └───────────────────────┴──────────────┘ │
│                                           │
│ ▶ Prozessor          ← eingeklappt       │
│ ▶ Arbeitsspeicher    ← eingeklappt       │
│ ▶ Display            ← eingeklappt       │
│ ▶ Konnektivität      ← eingeklappt       │
│ ▶ Akku & Energie     ← eingeklappt       │
└──────────────────────────────────────────┘
```

### Zustände

| Zustand | Anzeige |
|---|---|
| Noch keine Specs | Suchfeld + [Laden] + [↗ Geizhals] |
| COMPLETE | Bevorzugte Felder oben rechts + eingeklappte Gruppen |
| FAILED | ⚠️ "Keine Specs gefunden" — Term editierbar |
| QUOTA_EXCEEDED | 🚫 "Kontingent erschöpft bis [Datum]" |

### Verhalten
- Alle Gruppen standardmäßig **eingeklappt**
- Bevorzugte Felder oben rechts — immer sichtbar ohne Aufklappen
- Klick auf ☆ → ⭐ → Feld erscheint oben rechts
- Klick auf ⭐ → ☆ → Feld verschwindet oben rechts
- FAILED/QUOTA_EXCEEDED: Term editierbar, [Laden] aktiv für neuen Versuch

---

## Kapitel 3 — Kategorie-Präferenzen

### Design — Kombination Option X + Y

```
┌─────────────────────────────────────────┐
│ Kategorie-Präferenzen                    │
│                                          │
│ [Kategorie suchen...        🔍]          │
│                                          │
│ Aktive Präferenzen:                      │
│                                          │
│ Elektronik                CPU ⭐ RAM ⭐  │
│   └── Laptops             + Display ⭐   │
│                                          │
│ Drucker                   Duplex ⭐ ADF ⭐│
│                                          │
│ [+ Kategorie hinzufügen]                 │
└─────────────────────────────────────────┘
```

### Max. 5 Keywords
- Mehr als 5 Sterne setzen → blockiert:

```
⛔ Max. 5 Keywords für Suchanfrage erreicht
   Bitte zuerst ein anderes Feld entfernen
```

- Mehr als 5 Präferenzen gesamt möglich
- Nur erste 5 fließen in Brave-Query [Kap. 8]
- Rest als Pflichtfelder im LLM-Prompt [Kap. 9]

### Vererbungslogik (Phase 1)
```
Eigene Präferenz → Eltern → Großeltern → keine
```
Kind zeigt nur den **Unterschied** zur Elternkategorie.

**Phase 2 (später):** Override-Toggle pro Kategorie-Eintrag → [Kap. 14]

---

## Kapitel 4 — Settings-Struktur

```
Settings
├── Allgemein
│   ├── Theme (Dark/Light)
│   └── Datenbereinigung
├── API & Suche                    ⚠️  ← Warning wenn ≥ 80%
│   ├── Provider-Konfiguration
│   │   ├── Brave    ⚠️            ← Warning-Icon + Tooltip
│   │   ├── Groq
│   │   └── OpenRouter
│   └── Usage Monitor [Kap. 5]
└── Suchanpassung
    └── Kategorie-Präferenzen [Kap. 3]
```

Settings als **Expandable Cards** → Refactoring [Kap. 14]

Warning-Icon ⚠️ mit Tooltip:
```
"Bitte laut Provider-Dashboard eintragen
 ab welchem Tag deine Abrechnungsperiode startet"
```

---

## Kapitel 5 — Usage Monitor

### UI

```
┌─────────────────────────────────────────┐
│ API Nutzung                              │
│                                          │
│ Brave Search              [MONTHLY]      │
│   Periode:    15.03 – 14.04              │
│   Requests:   47 / 1.000                 │
│   Verbleibend: 953  (95%)                │
│   Ø Antwortzeit: 320ms                   │
│   ⚠️ Benachrichtigung ab 80% ausgeschöpft│
│                                          │
│ Groq (llama-3.1-8b-instant) [DAILY]      │
│   Tokens heute: 13.200 / 25.000          │
│   Verbleibend:  11.800  (47%)            │
│   Requests:     12                       │
│   Ø Antwortzeit: 850ms                   │
│   ⚠️ Benachrichtigung ab 80% ausgeschöpft│
└─────────────────────────────────────────┘
```

### Aggregation
- **DAILY**: `WHERE createdAt >= heute 00:00`
- **MONTHLY**: `WHERE createdAt >= period-start-day dieses Monats`

---

# FACHLICHES KONZEPT

---

## Kapitel 6 — Cache-Logik

```
Cache-Lookup für lookupTerm — ZUERST vor Kontingent-Check:

COMPLETE       → quickFactsJson sofort anzeigen
                 Button: [↺ Neu laden]
                 kein API-Call, kein Kontingent-Check nötig

FAILED         → Hinweis "Keine Specs gefunden"
                 Term editierbar für neuen Versuch
                 kein API-Call, kein Kontingent-Check nötig

QUOTA_EXCEEDED → wird behandelt wie nicht vorhanden
                 → weiter zu Kontingent-Check [Kap. 7]

nicht vorhanden → weiter zu Kontingent-Check [Kap. 7]
```

**Kein TTL** — Specs ändern sich nach Produktrelease nicht.
Löschen nur auf expliziten Wunsch (Datenbereinigung in Settings).

---

## Kapitel 7 — Kontingent-Logik

```
Kontingent-Check — nur wenn Cache kein Ergebnis liefert [Kap. 6]:

< 80%    → Normal, kein Hinweis → Brave Search [Kap. 8]
≥ 80%    → ⚠️ Warning-Icon in Settings [Kap. 4] → Brave Search [Kap. 8]
= 100%   → 🚫 Provider gesperrt:
           → kein API-Call
           → ProductLookup mit QUOTA_EXCEEDED anlegen
           → lookupTerm trotzdem in WhListingDetail speichern
           → Fehlermeldung: "Kontingent erschöpft bis [Datum]"

Nächster Aufruf bei QUOTA_EXCEEDED:
  → Cache-Check [Kap. 6] → QUOTA_EXCEEDED → hier weiter
  → Kontingent-Check:
      Neue Periode → QUOTA_EXCEEDED überschreiben → Brave Search [Kap. 8]
      Noch gesperrt → Fehlermeldung
```

Aktuell: Kontingent gilt **global** (Single-User).
Multi-User: Kontingent pro User-Key → [Kap. 14]

---

## Kapitel 8 — Brave Search: Dreistufige Query-Strategie

### Queries

```
Stufe 1:
  "[lookupTerm] Spezifikationen [pref1]..[pref5] site:icecat.biz"
  -filetype:pdf -"user guide" -"quick start" -"Bedienungsanleitung"
  → Treffer? → LLM [Kap. 9] → COMPLETE

Stufe 2:
  "[lookupTerm] Spezifikationen technische Daten site:icecat.biz"
  -filetype:pdf -"user guide" -"quick start" -"Bedienungsanleitung"
  → Treffer? → LLM [Kap. 9] → COMPLETE

Stufe 3:
  "[lookupTerm] site:icecat.biz"
  -filetype:pdf
  → Treffer? → LLM [Kap. 9] → COMPLETE
  → Keine Treffer? → FAILED

Alle Stufen: count=10, extra_snippets=true
```

### Warum site:icecat.biz?
Icecat liefert strukturierte Produktdaten mit `icecatId` in der URL:
```
✅ https://icecat.biz/p/lenovo-yoga-7-...-12345678.html
   → icecatId in URL vorhanden
   → Brave liefert lesbare Snippets mit Spec-Daten
```

### Warum -filetype:pdf?
```
❌ https://icecat.biz/p/lenovo-yoga-7-...-datasheet.pdf
   → icecatId nicht zuverlässig in URL
   → Brave kann aus PDFs keine verwertbaren Snippets extrahieren
   → -filetype:pdf filtert PDFs heraus
   → HTML-Produktseiten bleiben erreichbar
```

### Präferenz-Keywords
- Max. 5 Keywords aus gesetzten Präferenzen [Kap. 3]
- Reihenfolge: erste 5 der gesetzten Präferenzen
- "Spezifikationen" steht immer vor den Präferenzen
- Mehr als 5: Rest nur im LLM-Prompt als Pflichtfelder [Kap. 9]

### Brave Response-Struktur
```json
{
  "results": [
    {
      "title": "Lenovo Yoga 7 14IML9 - Icecat",
      "url": "https://icecat.biz/p/lenovo-...-12345678.html",
      "description": "Intel Core Ultra 7 155H, 16GB RAM...",
      "extra_snippets": [
        "Display: 14\" OLED 2880x1800 120Hz...",
        "Akku: 71 Wh..."
      ]
    }
  ]
}
```

Stufen sind **rein internes Implementierungsdetail** — User sieht nichts davon.

---

## Kapitel 9 — LLM-Verarbeitung

### Input ans LLM
```
- URLs der Brave-Treffer     ← icecatId extrahieren
- Snippets der Brave-Treffer ← Quick Facts extrahieren
- Pflichtfelder: präferierte Keywords der Kategorie [Kap. 3]
```

### Verarbeitung
```
LLM liest Snippets vollständig
  (Specs können auch hinten im Text stehen)
→ Quick Facts extrahieren
→ Pflichtfelder berücksichtigen
→ icecatId aus URL-Pattern extrahieren:
    https://icecat.biz/p/[slug]-[icecatId].html
                                  ↑ letzte Zahl vor .html
→ Sicherheitscheck (Java-Code):
    icecatId muss in einer der Brave-URLs vorkommen
    → JA: icecatId gültig
    → NEIN: icecatId = null (Halluzination verhindert)
```

### LLM Output-Struktur
```json
{
  "quickFacts": {
    "cpu": "Core Ultra 7 155H",
    "ram": "16 GB",
    "display": "14\" OLED 2880x1800 120Hz"
  },
  "sources": {
    "icecatId": "12345678",
    "icecatUrl": "https://icecat.biz/p/...-12345678.html"
  }
}
```

### Modell-Architektur
```
ExtractionClient (Interface)
├── GroqExtractionClient         ← primär empfohlen
└── OpenRouterExtractionClient   ← Alternative

ExtractionProviderRouter
└── liest active-provider aus application.yml
    → delegiert an passenden ExtractionClient
```

Provider-Wechsel: `querchecker.api.extraction.active-provider: GROQ → OPENROUTER` + Neustart.

### Empfohlene Modelle

| Aufgabe | Modell | Tier |
|---|---|---|
| Produktname aus Inseratstext | Groq `llama-3.1-8b-instant` | Free |
| Quick Facts + icecatId aus Snippets | Groq `llama-3.1-8b-instant` | Free |
| Fallback höhere Qualität | Groq `llama-3.3-70b-versatile` | Free |
| Alternative | OpenRouter `llama-3.3-70b-instruct:free` | Free |

---

## Kapitel 10 — Icecat: Vollständige Specs (optional)

```
[Alle Specs laden] geklickt
  → icecatId bereits aus Kap. 9 vorhanden?
      NEIN → Button deaktiviert, kein Call möglich
  → live.icecat.biz/api?icecat_id=[icecatId]&
      lang=DE&shopname=openIcecat-live
  → Vollständiger Icecat-Response (kein LLM-Extrakt)
  → Gecacht in ProductLookup.icecatSpecsJson
```

Demo-Zugang reicht (`openIcecat-live`) — keine Registrierung nötig.

---

# IMPLEMENTIERUNG

---

## Kapitel 11 — Datenstruktur

### `WhListingDetail` — erweitertes Feld
```
lookupTerm   String (nullable)
└── vom User bestätigter/korrigierter Term
    wird immer gespeichert — auch bei QUOTA_EXCEEDED
    beim erneuten Öffnen als Vorausfüllung verwendet
```

### `ProductLookup` — neues Entity
```
├── lookupTerm           String (Cache-Key, normalisiert)
├── lookupStatus         Enum:
│     COMPLETE           ← Specs gefunden (egal welche Stufe)
│     FAILED             ← alle Stufen erfolglos
│     QUOTA_EXCEEDED     ← nicht gesucht, Kontingent erschöpft
│                          → überschrieben wenn Kontingent wieder frei
├── quickFactsJson       JSON-Blob ← LLM-Extrakt aus Brave-Snippets
├── quickFactsFetchedAt  LocalDateTime
├── icecatId             String (nullable)
├── icecatUrl            String (nullable)
├── icecatSpecsJson      JSON-Blob (nullable) ← vollständig, kein Extrakt
├── icecatFetchedAt      LocalDateTime (nullable)
└── sourceUrls           String[]
```

### `CategorySpecPreference` — neues Entity
```
├── whCategoryId    FK → WhCategory
├── fieldKeys       String[] (["cpu", "ram"])
└── updatedAt       LocalDateTime
```

### `ApiUsageLog` — neues Entity
```
├── id
├── provider        Enum (BRAVE, GROQ, OPENROUTER, ICECAT)
├── requestType     Enum (SEARCH, EXTRACTION, SPEC_DETAIL)
├── lookupTerm      String (nullable)
├── responseStatus  Integer
├── tokensInput     Integer (nullable)
├── tokensOutput    Integer (nullable)
├── durationMs      Long
└── createdAt       LocalDateTime
```

Eintrag bei **jedem** echten API-Call — nie bei Cache-Hits.

### `DlCategoryPrompt` — erweitertes Entity

Bestehendes Entity um `promptType` und `systemPrompt` erweitert:

```
DlCategoryPrompt
├── id
├── whCategory      (ManyToOne, nullable)   ← null = Default
├── promptType      Enum (PRODUCT_NAME, QUICK_FACTS)   ← @Enumerated(EnumType.STRING)
├── systemPrompt    TEXT (nullable)          ← Fallback: hardcodierter Default
├── userPrompt      TEXT                     ← mit Platzhaltern
└── updatedAt       LocalDateTime
```

Unique Constraint: `(wh_category_id, prompt_type)` — pro Kategorie+Typ genau ein Eintrag.

**Platzhalter im `userPrompt`:**

| Platzhalter | Ersetzt durch |
|---|---|
| `{title}` | `WhListing.title` |
| `{description}` | `WhListing.description` (ggf. gekürzt) |
| `{category}` | `WhCategory.name` oder `"Allgemein"` |
| `{lookupTerm}` | Bestätigter Suchterm |
| `{snippets}` | Formatierte Brave-Ergebnisse (URL + Snippet-Block) |
| `{mandatoryFields}` | Komma-separierte Liste aus `CategorySpecPreferenceService` |

**Lookup-Logik** — `DlPromptResolver.resolve(category, promptType)`:
```
Eigene Kategorie + PromptType
    → Elternkategorie + PromptType
    → ... (rekursiv, beliebig tief)
    → Default (whCategory=null) + PromptType
```

### Vererbungslogik CategorySpecPreference

Rekursiv — funktioniert für beliebig tiefe Kategoriebäume:

```java
public CategorySpecPreference findWithInheritance(WhCategory category) {
    if (category == null) return null;
    return repo.findByWhCategory(category)
        .orElseGet(() -> findWithInheritance(category.getParent()));
}
```

- Eigene Präferenz → Elternkategorie → Großelternkategorie → ... → `null`
- Keine Hardcodierung auf 2 oder 3 Ebenen
- Bricht natürlich ab wenn `category.getParent() == null` (Wurzelknoten)

---

## Kapitel 12 — API-Konfiguration

### `application.yml` (in Git)
```yaml
querchecker:
  api:
    extraction:
      active-provider: GROQ   # GROQ | OPENROUTER — Neustart nötig bei Wechsel
    providers:
      brave:
        free-limit: 1000
        free-limit-period: MONTHLY
        period-start-day: 1
        limit-unit: REQUESTS
        alert-at-percent: 80
      groq:
        model: llama-3.1-8b-instant
        free-limit: 25000
        free-limit-period: DAILY
        period-start-day: 1
        limit-unit: TOKENS
        alert-at-percent: 80
      openrouter:
        model: meta-llama/llama-3.3-70b-instruct:free
        free-limit: 0
        free-limit-period: DAILY
        limit-unit: REQUESTS
        alert-at-percent: 80
```

### `secret.yml` (nie in Git)
```yaml
querchecker:
  api:
    providers:
      brave:
        api-key: ${BRAVE_API_KEY}
      groq:
        api-key: ${GROQ_API_KEY}
      openrouter:
        api-key: ${OPENROUTER_API_KEY}
```

### Kosten & Free Tiers

**Groq Free Tier:**
- `llama-3.1-8b-instant`: ~30.000 Tokens/Minute
- `llama-3.3-70b-versatile`: ~6.000 Tokens/Minute, ~500.000 Tokens/Tag
- Kein Kreditkartenzwang
- Token-Verbrauch: `usage.prompt_tokens` / `usage.completion_tokens`

**Brave Free Tier:**
- ~1.000 Requests/Monat ($5 Guthaben)
- Danach $5/1.000 Requests

**Kosten pro vollständigem Flow:**

| Schritt | Tokens | Kosten |
|---|---|---|
| Produktname-Extraktion (Groq 8B) | ~1.200 in / ~50 out | ~$0.00006 |
| Brave Search (site:icecat.biz) | — | ~$0.005 |
| Quick Facts + icecatId (Groq 8B) | ~950 in / ~150 out | ~$0.00006 |
| **Gesamt** | | **~$0.005** |

**Praktisch: Groq ist für Querchecker im Free Tier kostenlos.**

---

## Kapitel 13 — Caching & Rechtliches

| Quelle | Raw-Response cachen | LLM-Extrakt cachen |
|---|---|---|
| Brave | ❌ ToS-Verstoß | ✅ erlaubt |
| Groq | — | ✅ eigenes Werk |
| Icecat Open | ✅ explizit erlaubt | ✅ |
| Google CSE | ❌ nicht verfügbar (EOL 2027) | — |

---

# OFFEN

---

## Kapitel 14 — Offene Punkte

### Implementierung
```
- Fehlerfall: LLM liefert kein valides JSON
```

### Settings
```
- Settings als Expandable Cards umbauen (Refactoring)
- Kategorie-Präferenzen Phase 2:
  Override-Toggle pro Kategorie-Eintrag
```

### Multi-User & Multi-Tenancy (für später)
```
- Userverwaltung: Registrierung, Einladung, Rollen
- Key-Verwaltung: global vs. pro User
  → ApiUsageLog um userId erweitern
  → Limits und QUOTA_EXCEEDED pro User-Key
  → Userverwaltung als weiterer Settings-Bereich
- Demo-Zugang aktuell: HTTP Basic Auth (Single-User)
- Kontingent aktuell: global (Single-User)
```

---

# IMPLEMENTIERUNGS-PROMPTS

> Reihenfolge entspricht der empfohlenen Implementierungsreihenfolge.
> Jeder Prompt referenziert die relevanten Kapitel dieses Dokuments.

---

## Prompt A — Flyway Migration & Entities ✅ DURCHGEFÜHRT (2026-03-22)

**Parallelisierbar mit:** nichts, muss zuerst

**Aufgabe:**
Neue Entities und Flyway-Migrations anlegen basierend auf Kap. 11.

**Entities:**
- `ProductLookup` mit `LookupStatus` Enum (COMPLETE, FAILED, QUOTA_EXCEEDED)
- `CategorySpecPreference`
- `ApiUsageLog` mit `Provider` Enum (BRAVE, GROQ, OPENROUTER, ICECAT) ← `GOOGLE` via V29 zu `ICECAT` umbenannt
  und `RequestType` Enum (SEARCH, EXTRACTION, SPEC_DETAIL)
- `WhListingDetail` um Feld `lookupTerm` erweitern
- `DlCategoryPrompt` um `promptType` und `systemPrompt` erweitern [Kap. 11]

**Flyway Scripts:**
- `V{n}__add_lookup_term_to_wh_listing_detail.sql`
- `V{n+1}__create_product_lookup.sql`
- `V{n+2}__create_category_spec_preference.sql`
- `V{n+3}__create_api_usage_log.sql`
- `V{n+4}__alter_dl_category_prompt_add_prompt_type.sql`

**Migration `V{n+4}`:**
```sql
-- PromptType als VARCHAR (EnumType.STRING-Konvention des Projekts)
ALTER TABLE dl_category_prompt
    ADD COLUMN prompt_type  VARCHAR(30) NOT NULL DEFAULT 'PRODUCT_NAME',
    ADD COLUMN system_prompt TEXT,
    RENAME COLUMN prompt TO user_prompt;

-- Bestehende Einträge sind PRODUCT_NAME (Default greift)
-- Default danach entfernen — Pflichtfeld ab jetzt
ALTER TABLE dl_category_prompt
    ALTER COLUMN prompt_type DROP DEFAULT;

-- Unique Constraint: pro Kategorie+Typ genau ein Eintrag
-- null-Kategorie (Default) ist ebenfalls eindeutig pro Typ
ALTER TABLE dl_category_prompt
    DROP CONSTRAINT IF EXISTS uq_dl_category_prompt_category,
    ADD CONSTRAINT uq_dl_category_prompt_category_type
        UNIQUE (wh_category_id, prompt_type);
```

**Entity `DlCategoryPrompt` anpassen:**
```java
@Entity
@Table(name = "dl_category_prompt")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DlCategoryPrompt {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wh_category_id")
    private WhCategory whCategory;  // null = Default

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromptType promptType;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;    // nullable — Fallback: hardcodierter Default

    @Column(nullable = false, columnDefinition = "TEXT")
    private String userPrompt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    public void updateTimestamp() { this.updatedAt = LocalDateTime.now(); }
}
```

**`PromptType` Enum:**
```java
public enum PromptType {
    PRODUCT_NAME,   // Produktname aus Inseratstext extrahieren
    QUICK_FACTS     // Technische Specs aus Brave-Snippets extrahieren
}
```

**Repository anpassen:**
```java
public interface DlCategoryPromptRepository extends JpaRepository<DlCategoryPrompt, Long> {
    Optional<DlCategoryPrompt> findByWhCategoryAndPromptType(
        WhCategory category, PromptType promptType);

    @Query("SELECT p FROM DlCategoryPrompt p " +
           "WHERE p.whCategory IS NULL AND p.promptType = :promptType")
    Optional<DlCategoryPrompt> findDefaultByPromptType(
        @Param("promptType") PromptType promptType);
}
```

---

## Prompt B — API-Konfiguration & Properties ✅ DURCHGEFÜHRT (2026-03-22)

**Parallelisierbar mit:** Prompt A

**Aufgabe:**
Provider-Konfiguration implementieren basierend auf Kap. 12.

- `ProviderProperties` als `@ConfigurationProperties`
- Mapping für BRAVE, GROQ, OPENROUTER
- Keys aus `secret.yml`
- Limits, Perioden, alert-at-percent aus `application.yml`

---

## Prompt B2 — ExtractionClient Interface, Router & Prompt-Seeder ✅ DURCHGEFÜHRT (2026-03-22)

**Parallelisierbar mit:** Prompt C
**Setzt voraus:** Prompt A, B

**Aufgabe:**
Provider-Abstraktion und Prompt-Seeding implementieren basierend auf Kap. 9 und Kap. 11.

---

### ExtractionClient Interface

```java
/**
 * Provider-unabhängiges Interface für LLM-Extraktion.
 * Implementierungen: GroqExtractionClient, OpenRouterExtractionClient
 * Aktiver Provider: querchecker.api.extraction.active-provider in application.yml
 */
public interface ExtractionClient {

    /** Produktname aus Inseratstext — kurzer String, kein JSON */
    String extractProductName(
        String title,
        String description,
        String categoryName,
        DlCategoryPrompt prompt  // aus DB, aufgelöst via DlPromptResolver
    );

    /** Quick Facts + icecatId aus Brave-Snippets — JSON-Response */
    QuickFactsResult extractQuickFacts(
        String lookupTerm,
        String categoryName,
        List<BraveResult> braveResults,
        List<String> mandatoryFields,
        DlCategoryPrompt prompt  // aus DB, aufgelöst via DlPromptResolver
    );
}
```

### ExtractionProviderRouter

```java
@Service
@RequiredArgsConstructor
public class ExtractionProviderRouter {

    private final ExtractionProperties extractionProperties;
    private final List<ExtractionClient> clients;

    public ExtractionClient getActive() {
        Provider active = extractionProperties.getActiveProvider();
        return clients.stream()
            .filter(c -> c.getProvider() == active)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Kein ExtractionClient für Provider: " + active));
    }
}
```

`application.yml` Ergänzung:
```yaml
querchecker:
  api:
    extraction:
      active-provider: GROQ   # GROQ | OPENROUTER
```

### DlPromptResolver — Signatur erweitern

Bestehender `DlPromptResolver` bekommt den `promptType` als Parameter.
Rekursive Vererbungslogik bleibt identisch [Kap. 11]:

```java
@Service
@RequiredArgsConstructor
public class DlPromptResolver {

    private final DlCategoryPromptRepository promptRepo;

    /** Löst Prompt auf: Eigene Kategorie → Eltern → ... → Default (whCategory=null) */
    public DlCategoryPrompt resolve(WhCategory category, PromptType promptType) {
        return resolveRecursive(category, promptType);
    }

    private DlCategoryPrompt resolveRecursive(WhCategory category, PromptType promptType) {
        if (category == null) {
            return promptRepo.findDefaultByPromptType(promptType)
                .orElseThrow(() -> new IllegalStateException(
                    "Kein Default-Prompt für PromptType: " + promptType));
        }
        return promptRepo.findByWhCategoryAndPromptType(category, promptType)
            .orElseGet(() -> resolveRecursive(category.getParent(), promptType));
    }
}
```

### DlCategoryPromptSeeder — erweitern

Bestehender Seeder wird um `QUICK_FACTS`-Prompts und `systemPrompt` erweitert.
`seedIfAbsent()` prüft neu auf `count() == 0` **pro PromptType**.

**Hardcodierte Fallback-Prompts** (in `DlCategoryPromptDefinitions`):

```java
public final class DlCategoryPromptDefinitions {

    // --- PRODUCT_NAME ---

    public static final String PRODUCT_NAME_SYSTEM =
        """
        Du extrahierst Produktbezeichnungen aus Kleinanzeigen-Texten.
        Antworte NUR mit dem Produktnamen — kein erklärender Text, keine Sätze.
        Wenn kein eindeutiger Produktname erkennbar ist, antworte mit: UNBEKANNT
        """;

    public static final String PRODUCT_NAME_USER_DEFAULT =
        """
        Kategorie: {category}
        Titel: {title}

        Beschreibung:
        {description}

        Extrahiere den genauen Produktnamen oder die Modellbezeichnung.
        Beispiele für gute Antworten:
        - ThinkPad X1 Carbon Gen 12
        - Samsung Galaxy S24 Ultra
        - HP LaserJet Pro M404dn
        """;

    // --- QUICK_FACTS ---

    public static final String QUICK_FACTS_SYSTEM =
        """
        Du extrahierst technische Spezifikationen aus Produktseiten-Snippets.
        Antworte NUR mit validem JSON — kein erklärender Text, keine Markdown-Backticks.
        Wenn ein Wert nicht erkennbar ist, lass das Feld weg (kein null, kein "unbekannt").
        Feldnamen im quickFacts-Objekt: Kleinbuchstaben, Englisch, keine Sonderzeichen.
        """;

    public static final String QUICK_FACTS_USER_DEFAULT =
        """
        Produkt: {lookupTerm}
        Kategorie: {category}

        Suchergebnisse:
        {snippets}

        Pflichtfelder (müssen erscheinen wenn erkennbar):
        {mandatoryFields}

        Antworte mit diesem JSON-Schema:
        {
          "quickFacts": {
            "cpu": "...",
            "ram": "..."
          },
          "sources": {
            "icecatId": "letzte Zahl vor .html aus der relevantesten URL",
            "icecatUrl": "vollständige URL des relevantesten Treffers"
          }
        }
        """;

    // Kategorie-spezifische User-Prompts (optional — Default reicht meist)
    public static final Map<String, String> QUICK_FACTS_USER_BY_CATEGORY = Map.ofEntries(
        Map.entry("Laptop / Notebook",
            """
            Produkt: {lookupTerm}
            Kategorie: Laptop / Notebook

            Suchergebnisse:
            {snippets}

            Pflichtfelder (müssen erscheinen wenn erkennbar):
            {mandatoryFields}

            Relevante Felder für Laptops: cpu, ram, storage, display, battery, weight, os

            Antworte mit diesem JSON-Schema:
            {
              "quickFacts": { "cpu": "...", "ram": "...", "display": "..." },
              "sources": { "icecatId": "...", "icecatUrl": "..." }
            }
            """),
        Map.entry("Drucker & Scanner",
            """
            Produkt: {lookupTerm}
            Kategorie: Drucker & Scanner

            Suchergebnisse:
            {snippets}

            Pflichtfelder (müssen erscheinen wenn erkennbar):
            {mandatoryFields}

            Relevante Felder für Drucker: technology, color, duplex, adf, ppm_mono, ppm_color, connectivity

            Antworte mit diesem JSON-Schema:
            {
              "quickFacts": { "technology": "...", "duplex": "...", "ppm_mono": "..." },
              "sources": { "icecatId": "...", "icecatUrl": "..." }
            }
            """)
    );
}
```

**Platzhalter** die der `ExtractionClient` vor dem API-Call ersetzt:

| Platzhalter | Ersetzt durch |
|---|---|
| `{title}` | `WhListing.title` |
| `{description}` | `WhListing.description` (ggf. gekürzt) |
| `{category}` | `WhCategory.name` oder `"Allgemein"` |
| `{lookupTerm}` | Bestätigter Suchterm |
| `{snippets}` | Formatierte Brave-Ergebnisse (URL + Snippet-Block) |
| `{mandatoryFields}` | Komma-separierte Liste aus `CategorySpecPreferenceService` |

### JUnit Tests

```java
@ExtendWith(MockitoExtension.class)
class DlPromptResolverTest {

    @Mock DlCategoryPromptRepository repo;
    @InjectMocks DlPromptResolver resolver;

    @Test
    void resolve_returnsOwnPrompt_whenDirectMatch() {
        WhCategory laptops = category("Laptops", null);
        DlCategoryPrompt prompt = prompt(PromptType.QUICK_FACTS, "Laptop-Prompt");
        when(repo.findByWhCategoryAndPromptType(laptops, PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(prompt));

        assertThat(resolver.resolve(laptops, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Laptop-Prompt");
    }

    @Test
    void resolve_inheritsFromParent_whenNoOwnPrompt() {
        WhCategory root    = category("Elektronik", null);
        WhCategory laptops = category("Laptops", root);
        DlCategoryPrompt parentPrompt = prompt(PromptType.QUICK_FACTS, "Elektronik-Prompt");

        when(repo.findByWhCategoryAndPromptType(laptops, PromptType.QUICK_FACTS))
            .thenReturn(Optional.empty());
        when(repo.findByWhCategoryAndPromptType(root, PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(parentPrompt));

        assertThat(resolver.resolve(laptops, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Elektronik-Prompt");
    }

    @Test
    void resolve_inheritsRecursively_acrossThreeLevels() {
        WhCategory root  = category("Elektronik", null);
        WhCategory mid   = category("Computer", root);
        WhCategory leaf  = category("Gaming-Laptops", mid);
        DlCategoryPrompt defaultPrompt = prompt(PromptType.QUICK_FACTS, "Root-Prompt");

        when(repo.findByWhCategoryAndPromptType(leaf, PromptType.QUICK_FACTS))
            .thenReturn(Optional.empty());
        when(repo.findByWhCategoryAndPromptType(mid, PromptType.QUICK_FACTS))
            .thenReturn(Optional.empty());
        when(repo.findByWhCategoryAndPromptType(root, PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(defaultPrompt));

        assertThat(resolver.resolve(leaf, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Root-Prompt");
    }

    @Test
    void resolve_returnsDefault_whenNoCategoryMatch() {
        WhCategory root = category("Elektronik", null);
        DlCategoryPrompt defaultPrompt = prompt(PromptType.QUICK_FACTS, "Default-Prompt");

        when(repo.findByWhCategoryAndPromptType(any(WhCategory.class), any()))
            .thenReturn(Optional.empty());
        when(repo.findDefaultByPromptType(PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(defaultPrompt));

        assertThat(resolver.resolve(root, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Default-Prompt");
    }

    @Test
    void resolve_worksIndependentlyPerPromptType() {
        // PRODUCT_NAME und QUICK_FACTS liefern unterschiedliche Prompts
        WhCategory laptops = category("Laptops", null);
        when(repo.findByWhCategoryAndPromptType(laptops, PromptType.PRODUCT_NAME))
            .thenReturn(Optional.of(prompt(PromptType.PRODUCT_NAME, "Name-Prompt")));
        when(repo.findByWhCategoryAndPromptType(laptops, PromptType.QUICK_FACTS))
            .thenReturn(Optional.of(prompt(PromptType.QUICK_FACTS, "Facts-Prompt")));

        assertThat(resolver.resolve(laptops, PromptType.PRODUCT_NAME).getUserPrompt())
            .isEqualTo("Name-Prompt");
        assertThat(resolver.resolve(laptops, PromptType.QUICK_FACTS).getUserPrompt())
            .isEqualTo("Facts-Prompt");
    }

    @Test
    void resolve_throwsException_whenNoDefaultInDB() {
        when(repo.findByWhCategoryAndPromptType(any(), any())).thenReturn(Optional.empty());
        when(repo.findDefaultByPromptType(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(null, PromptType.QUICK_FACTS))
            .isInstanceOf(IllegalStateException.class);
    }

    private WhCategory category(String name, WhCategory parent) {
        WhCategory c = new WhCategory(); c.setName(name); c.setParent(parent); return c;
    }
    private DlCategoryPrompt prompt(PromptType type, String userPrompt) {
        return DlCategoryPrompt.builder().promptType(type).userPrompt(userPrompt).build();
    }
}
```

---

## Prompt C — ApiUsageLog Service ✅ DURCHGEFÜHRT (2026-03-22)

**Setzt voraus:** Prompt A

**Aufgabe:**
`ApiUsageLogService` implementieren.

- `log(provider, requestType, lookupTerm, responseStatus, tokensIn, tokensOut, durationMs)`
- Eintrag bei jedem echten API-Call
- Aggregations-Methoden für Usage Monitor [Kap. 5]:
  - `countByProviderAndPeriod(provider, from, to)`
  - `sumTokensInputByProviderAndPeriod(provider, from, to)` ← getrennt nach IN/OUT
  - `sumTokensOutputByProviderAndPeriod(provider, from, to)`
  - `avgDurationByProvider()` entfernt

**JUnit Tests:**

```java
@ExtendWith(MockitoExtension.class)
class ApiUsageLogServiceTest {

    @Mock ApiUsageLogRepository repo;
    @InjectMocks ApiUsageLogService service;

    @Test
    void log_savesEntryWithAllFields() {
        service.log(Provider.BRAVE, RequestType.SEARCH, "ThinkPad X1", 200, null, null, 320L);

        verify(repo).save(argThat(entry ->
            entry.getProvider()     == Provider.BRAVE   &&
            entry.getRequestType()  == RequestType.SEARCH &&
            entry.getLookupTerm()   .equals("ThinkPad X1") &&
            entry.getResponseStatus() == 200            &&
            entry.getDurationMs()   == 320L             &&
            entry.getCreatedAt()    != null
        ));
    }

    @Test
    void log_acceptsNullTokens_forBraveSearch() {
        // Brave liefert keine Token-Counts
        assertThatNoException().isThrownBy(() ->
            service.log(Provider.BRAVE, RequestType.SEARCH, "test", 200, null, null, 100L));
    }

    @Test
    void log_savesTokens_forGroqExtraction() {
        service.log(Provider.GROQ, RequestType.EXTRACTION, "ThinkPad", 200, 950, 150, 850L);

        verify(repo).save(argThat(entry ->
            entry.getTokensInput()  == 950 &&
            entry.getTokensOutput() == 150
        ));
    }

    @Test
    void countByProviderAndPeriod_delegatesToRepository() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to   = LocalDateTime.now();
        when(repo.countByProviderAndCreatedAtBetween(Provider.BRAVE, from, to)).thenReturn(47L);

        assertThat(service.countByProviderAndPeriod(Provider.BRAVE, from, to)).isEqualTo(47L);
    }

    @Test
    void sumTokensByProviderAndPeriod_delegatesToRepository() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to   = LocalDateTime.now();
        when(repo.sumTokensByProviderAndCreatedAtBetween(Provider.GROQ, from, to))
            .thenReturn(13200L);

        assertThat(service.sumTokensByProviderAndPeriod(Provider.GROQ, from, to))
            .isEqualTo(13200L);
    }

    @Test
    void sumTokensByProviderAndPeriod_returnsZero_whenNoEntries() {
        when(repo.sumTokensByProviderAndCreatedAtBetween(any(), any(), any()))
            .thenReturn(null); // SUM() gibt NULL zurück wenn keine Zeilen

        assertThat(service.sumTokensByProviderAndPeriod(Provider.GROQ,
            LocalDateTime.now().minusDays(1), LocalDateTime.now())).isZero();
    }
}
```

---

## Prompt D — Kontingent-Service ✅ DURCHGEFÜHRT (2026-03-22)

**Setzt voraus:** Prompt A, B, C

**Aufgabe:**
`QuotaService` implementieren basierend auf Kap. 7.

- `checkQuota(provider)` → OK | QUOTA_EXCEEDED
- Berechnung der aktuellen Periode (DAILY/MONTHLY + period-start-day)
- Vergleich aktueller Verbrauch gegen free-limit
- `isWarningThreshold(provider)` → boolean (≥ alert-at-percent)
- `getQuotaResetDate(provider)` → LocalDate

**JUnit Tests:**

```java
@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock ApiUsageLogService usageLogService;
    @Mock ProviderProperties providerProperties;
    @InjectMocks QuotaService service;

    // --- Periodberechnungen ---

    @Test
    void getPeriodStart_daily_returnsTodayMidnight() {
        LocalDate periodStart = service.getPeriodStart(FreeLimitPeriod.DAILY, 1);
        assertThat(periodStart).isEqualTo(LocalDate.now());
    }

    @Test
    void getPeriodStart_monthly_returnsCurrentMonthStart_whenPeriodStartDay1() {
        LocalDate periodStart = service.getPeriodStart(FreeLimitPeriod.MONTHLY, 1);
        assertThat(periodStart).isEqualTo(LocalDate.now().withDayOfMonth(1));
    }

    @Test
    void getPeriodStart_monthly_returnsPreviousMonth_whenTodayBeforePeriodStartDay() {
        // Heute ist der 10., Periode startet am 15. → voriger Monat
        LocalDate today = LocalDate.of(2026, 3, 10);
        LocalDate expected = LocalDate.of(2026, 2, 15);
        assertThat(service.getPeriodStart(FreeLimitPeriod.MONTHLY, 15, today))
            .isEqualTo(expected);
    }

    @Test
    void getPeriodStart_monthly_returnsCurrentMonth_whenTodayIsPeriodStartDay() {
        LocalDate today = LocalDate.of(2026, 3, 15);
        LocalDate expected = LocalDate.of(2026, 3, 15);
        assertThat(service.getPeriodStart(FreeLimitPeriod.MONTHLY, 15, today))
            .isEqualTo(expected);
    }

    // --- checkQuota ---

    @Test
    void checkQuota_returnsOk_whenUnderLimit() {
        givenBraveConfig(1000, 80);
        when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any()))
            .thenReturn(500L);

        assertThat(service.checkQuota(Provider.BRAVE)).isEqualTo(QuotaStatus.OK);
    }

    @Test
    void checkQuota_returnsQuotaExceeded_whenAtLimit() {
        givenBraveConfig(1000, 80);
        when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any()))
            .thenReturn(1000L);

        assertThat(service.checkQuota(Provider.BRAVE)).isEqualTo(QuotaStatus.QUOTA_EXCEEDED);
    }

    @Test
    void checkQuota_returnsQuotaExceeded_whenOverLimit() {
        givenBraveConfig(1000, 80);
        when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any()))
            .thenReturn(1050L);

        assertThat(service.checkQuota(Provider.BRAVE)).isEqualTo(QuotaStatus.QUOTA_EXCEEDED);
    }

    // --- isWarningThreshold ---

    @Test
    void isWarningThreshold_returnsFalse_whenBelow80Percent() {
        givenBraveConfig(1000, 80);
        when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any()))
            .thenReturn(799L);

        assertThat(service.isWarningThreshold(Provider.BRAVE)).isFalse();
    }

    @Test
    void isWarningThreshold_returnsTrue_whenAtExact80Percent() {
        givenBraveConfig(1000, 80);
        when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any()))
            .thenReturn(800L);

        assertThat(service.isWarningThreshold(Provider.BRAVE)).isTrue();
    }

    @Test
    void isWarningThreshold_returnsTrue_whenAbove80Percent() {
        givenBraveConfig(1000, 80);
        when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any()))
            .thenReturn(950L);

        assertThat(service.isWarningThreshold(Provider.BRAVE)).isTrue();
    }

    private void givenBraveConfig(int freeLimit, int alertAtPercent) {
        ProviderConfig config = new ProviderConfig();
        config.setFreeLimit(freeLimit);
        config.setFreeLimitPeriod(FreeLimitPeriod.MONTHLY);
        config.setPeriodStartDay(1);
        config.setLimitUnit(LimitUnit.REQUESTS);
        config.setAlertAtPercent(alertAtPercent);
        when(providerProperties.getProvider(Provider.BRAVE)).thenReturn(config);
    }
}
```

---

## Prompt E — BraveSearchService ✅ DURCHGEFÜHRT

**Setzt voraus:** Prompt B, C

**Aufgabe:**
`BraveSearchService` implementieren basierend auf Kap. 8.

- `search(lookupTerm, preferenceKeywords)` → `List<BraveResult>`
- Dreistufige Query-Strategie (Stufe 1, 2, 3)
- Negative Filter pro Stufe
- `count=10, extra_snippets=true`
- `ApiUsageLog` Eintrag nach jedem Call
- `BraveResult` DTO: `{ title, url, description, extraSnippets }`

**JUnit Tests:**

```java
@ExtendWith(MockitoExtension.class)
class BraveSearchServiceTest {

    @Mock RestTemplate restTemplate;
    @Mock ApiUsageLogService usageLogService;
    @Mock ProviderProperties providerProperties;
    @InjectMocks BraveSearchService service;

    @Test
    void search_returnsResults_onFirstStageSucess() {
        givenBraveReturns(stageOneUrl(), List.of(braveResult("Lenovo Yoga 7", "https://icecat.biz/p/lenovo-yoga-7-12345678.html")));

        List<BraveResult> results = service.search("Lenovo Yoga 7", List.of("cpu", "ram"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Lenovo Yoga 7");
        // Nur 1 Brave-Call — Stufe 2 und 3 nicht ausgeführt
        verify(restTemplate, times(1)).exchange(any(), any(), any(), eq(BraveApiResponse.class));
    }

    @Test
    void search_fallsToStageTwo_whenStageOneEmpty() {
        givenBraveReturns(stageOneUrl(), List.of());
        givenBraveReturns(stageTwoUrl(), List.of(braveResult("Lenovo Yoga", "https://icecat.biz/p/lenovo-12345678.html")));

        List<BraveResult> results = service.search("Lenovo Yoga 7", List.of("cpu"));

        assertThat(results).hasSize(1);
        verify(restTemplate, times(2)).exchange(any(), any(), any(), eq(BraveApiResponse.class));
    }

    @Test
    void search_fallsToStageThree_whenStageOneAndTwoEmpty() {
        givenBraveReturns(stageOneUrl(), List.of());
        givenBraveReturns(stageTwoUrl(), List.of());
        givenBraveReturns(stageThreeUrl(), List.of(braveResult("Lenovo", "https://icecat.biz/p/12345678.html")));

        List<BraveResult> results = service.search("Lenovo Yoga 7", List.of());

        assertThat(results).hasSize(1);
        verify(restTemplate, times(3)).exchange(any(), any(), any(), eq(BraveApiResponse.class));
    }

    @Test
    void search_returnsEmpty_whenAllStagesEmpty() {
        givenBraveReturns(stageOneUrl(),   List.of());
        givenBraveReturns(stageTwoUrl(),   List.of());
        givenBraveReturns(stageThreeUrl(), List.of());

        assertThat(service.search("Unbekanntes Gerät", List.of())).isEmpty();
    }

    @Test
    void search_logsUsage_afterEachCall() {
        givenBraveReturns(stageOneUrl(), List.of());
        givenBraveReturns(stageTwoUrl(), List.of());
        givenBraveReturns(stageThreeUrl(), List.of());

        service.search("test", List.of());

        // 3 Stufen → 3 Log-Einträge
        verify(usageLogService, times(3)).log(eq(Provider.BRAVE), eq(RequestType.SEARCH),
            any(), anyInt(), isNull(), isNull(), anyLong());
    }

    @Test
    void search_queryContainsSiteIcecat() {
        // Alle Stufen müssen site:icecat.biz enthalten
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        givenBraveReturns(any(), List.of());
        service.search("ThinkPad X1", List.of());
        verify(restTemplate, atLeastOnce()).exchange(
            urlCaptor.capture(), any(), any(), any());
        assertThat(urlCaptor.getAllValues())
            .allMatch(url -> url.contains("site%3Aicecat.biz") || url.contains("site:icecat.biz"));
    }

    // Hilfsmethoden
    private void givenBraveReturns(String url, List<BraveResult> results) { /* ... */ }
    private BraveResult braveResult(String title, String url) { /* ... */ }
    private String stageOneUrl()   { return "stufe-1-url-pattern"; }
    private String stageTwoUrl()   { return "stufe-2-url-pattern"; }
    private String stageThreeUrl() { return "stufe-3-url-pattern"; }
}
```

---

## Prompt F — GroqExtractionClient & OpenRouterExtractionClient ✅ DURCHGEFÜHRT

**Setzt voraus:** Prompt B, B2, C

**Aufgabe:**
Konkrete `ExtractionClient`-Implementierungen basierend auf Kap. 9.
Interface und Router sind bereits in Prompt B2 angelegt.

### GroqExtractionClient

```java
@Component
@RequiredArgsConstructor
public class GroqExtractionClient implements ExtractionClient {

    private final RestClient restClient;
    private final ProviderProperties providerProperties;
    private final ApiUsageLogService usageLogService;

    @Override
    public Provider getProvider() { return Provider.GROQ; }

    @Override
    public String extractProductName(String title, String description,
                                     String categoryName, DlCategoryPrompt prompt) {
        String userPrompt = prompt.getUserPrompt()
            .replace("{title}",       title)
            .replace("{description}", truncate(description, 800))
            .replace("{category}",    categoryName);

        GroqResponse response = callGroq(prompt.getSystemPrompt(), userPrompt);
        logUsage(RequestType.EXTRACTION, null, response);
        return response.firstChoice().trim();
    }

    @Override
    public QuickFactsResult extractQuickFacts(String lookupTerm, String categoryName,
                                              List<BraveResult> braveResults,
                                              List<String> mandatoryFields,
                                              DlCategoryPrompt prompt) {
        String snippetsBlock = formatSnippets(braveResults);
        String userPrompt = prompt.getUserPrompt()
            .replace("{lookupTerm}",      lookupTerm)
            .replace("{category}",        categoryName)
            .replace("{snippets}",        snippetsBlock)
            .replace("{mandatoryFields}", String.join(", ", mandatoryFields));

        GroqResponse response = callGroq(prompt.getSystemPrompt(), userPrompt);
        logUsage(RequestType.EXTRACTION, lookupTerm, response);

        QuickFactsResult result = parseJson(response.firstChoice());
        return applyIcecatIdSafetyCheck(result, braveResults);
    }

    private QuickFactsResult applyIcecatIdSafetyCheck(QuickFactsResult result,
                                                        List<BraveResult> braveResults) {
        // icecatId muss in einer Brave-URL vorkommen — sonst Halluzination
        String icecatId = result.getSources().getIcecatId();
        if (icecatId == null) return result;
        boolean foundInUrls = braveResults.stream()
            .anyMatch(r -> r.getUrl().contains(icecatId));
        if (!foundInUrls) {
            result.getSources().setIcecatId(null);
            result.getSources().setIcecatUrl(null);
        }
        return result;
    }

    private String formatSnippets(List<BraveResult> results) {
        return results.stream().map(r ->
            "---\nURL: " + r.getUrl() + "\nTitel: " + r.getTitle() +
            "\nSnippet: " + r.getDescription() +
            (r.getExtraSnippets().isEmpty() ? "" :
                "\nExtra: " + String.join(" | ", r.getExtraSnippets()))
        ).collect(Collectors.joining("\n"));
    }
}
```

### OpenRouterExtractionClient

Analog zu `GroqExtractionClient` — Endpoint und Auth-Header unterscheiden sich:

```java
// Groq:       POST https://api.groq.com/openai/v1/chat/completions
// OpenRouter: POST https://openrouter.ai/api/v1/chat/completions
//             Header: HTTP-Referer: https://querchecker.at (empfohlen)
```

Sonst identisch — gleiche Request/Response-Struktur (OpenAI-kompatibel).

**JUnit Tests:**

```java
@ExtendWith(MockitoExtension.class)
class GroqExtractionClientTest {

    @Mock RestClient restClient;
    @Mock ApiUsageLogService usageLogService;
    @InjectMocks GroqExtractionClient client;

    @Test
    void extractProductName_replacesAllPlaceholders() {
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        givenGroqReturns("ThinkPad X1 Carbon Gen 12");
        DlCategoryPrompt prompt = prompt(
            "Du extrahierst Produktnamen.",
            "Kategorie: {category}\nTitel: {title}\n\n{description}");

        client.extractProductName("ThinkPad Laptop", "sehr gepflegt", "Laptop / Notebook", prompt);

        // Alle Platzhalter müssen ersetzt sein
        verify(restClient).post(); // vereinfacht — echter Test prüft Request-Body
        // Kein {category}, {title}, {description} mehr im gesendeten Body
    }

    @Test
    void extractProductName_truncatesLongDescription() {
        givenGroqReturns("HP LaserJet");
        DlCategoryPrompt prompt = prompt("system", "{description}");
        String longDesc = "x".repeat(2000);

        // Kein Fehler, keine Exception — gekürzt auf max Länge
        assertThatNoException().isThrownBy(() ->
            client.extractProductName("HP", longDesc, "Drucker", prompt));
    }

    @Test
    void extractQuickFacts_icecatIdNull_whenNotInBraveUrls() {
        givenGroqReturns("""
            {
              "quickFacts": { "cpu": "Core i7" },
              "sources": { "icecatId": "99999", "icecatUrl": "https://icecat.biz/p/fake-99999.html" }
            }
            """);
        List<BraveResult> results = List.of(
            braveResult("https://icecat.biz/p/lenovo-12345.html"));  // andere ID

        QuickFactsResult result = client.extractQuickFacts(
            "ThinkPad", "Laptop", results, List.of(), prompt("s", "u"));

        assertThat(result.getSources().getIcecatId()).isNull();
    }

    @Test
    void extractQuickFacts_icecatIdValid_whenFoundInBraveUrls() {
        givenGroqReturns("""
            {
              "quickFacts": { "cpu": "Core Ultra 7" },
              "sources": { "icecatId": "12345", "icecatUrl": "https://icecat.biz/p/lenovo-12345.html" }
            }
            """);
        List<BraveResult> results = List.of(
            braveResult("https://icecat.biz/p/lenovo-12345.html"));  // ID stimmt

        QuickFactsResult result = client.extractQuickFacts(
            "ThinkPad", "Laptop", results, List.of(), prompt("s", "u"));

        assertThat(result.getSources().getIcecatId()).isEqualTo("12345");
    }

    @Test
    void extractQuickFacts_logsTokenUsage() {
        givenGroqReturns("""{ "quickFacts": {}, "sources": {} }""", 950, 150);

        client.extractQuickFacts("ThinkPad", "Laptop", List.of(), List.of(), prompt("s", "u"));

        verify(usageLogService).log(eq(Provider.GROQ), eq(RequestType.EXTRACTION),
            any(), anyInt(), eq(950), eq(150), anyLong());
    }

    @Test
    void extractQuickFacts_handlesInvalidJson_withoutException() {
        givenGroqReturns("kein JSON");
        assertThatNoException().isThrownBy(() ->
            client.extractQuickFacts("test", "cat", List.of(), List.of(), prompt("s", "u")));
    }

    private void givenGroqReturns(String content) { givenGroqReturns(content, 500, 100); }
    private void givenGroqReturns(String content, int tokensIn, int tokensOut) { /* mock */ }
    private BraveResult braveResult(String url) { /* ... */ }
    private DlCategoryPrompt prompt(String system, String user) {
        return DlCategoryPrompt.builder().systemPrompt(system).userPrompt(user).build();
    }
}
```

---

## Prompt G — CategorySpecPreferenceService

**Setzt voraus:** Prompt A

**Aufgabe:**
`CategorySpecPreferenceService` implementieren basierend auf Kap. 3 und Kap. 9.

**Methoden:**
- `getPreferences(whCategory)` → `List<String> fieldKeys`
- `setPreferences(whCategory, fieldKeys)` — max. 5 Keywords validieren
- `getQueryKeywords(whCategory)` → erste 5 für Brave-Query
- `getMandatoryFields(whCategory)` → alle für LLM-Pflichtfelder

**Vererbungslogik — rekursiv (Kap. 11):**
```java
private CategorySpecPreference findWithInheritance(WhCategory category) {
    if (category == null) return null;
    return repo.findByWhCategory(category)
        .orElseGet(() -> findWithInheritance(category.getParent()));
}
```
Funktioniert für beliebig tiefe Bäume — keine Hardcodierung auf Ebenenanzahl.

**JUnit Tests:**

```java
@ExtendWith(MockitoExtension.class)
class CategorySpecPreferenceServiceTest {

    @Mock CategorySpecPreferenceRepository repo;
    @InjectMocks CategorySpecPreferenceService service;

    // Hilfsmethode: Kategorie-Hierarchie aufbauen
    private WhCategory category(String name, WhCategory parent) {
        WhCategory cat = new WhCategory();
        cat.setName(name);
        cat.setParent(parent);
        return cat;
    }

    private CategorySpecPreference pref(String... keys) {
        CategorySpecPreference p = new CategorySpecPreference();
        p.setFieldKeys(List.of(keys));
        return p;
    }

    // --- Vererbungslogik ---

    @Test
    void getPreferences_returnsOwnPref_whenDirectMatch() {
        WhCategory laptops = category("Laptops", null);
        when(repo.findByWhCategory(laptops))
            .thenReturn(Optional.of(pref("cpu", "ram")));

        assertThat(service.getPreferences(laptops))
            .containsExactly("cpu", "ram");
    }

    @Test
    void getPreferences_inheritsFromParent_whenNoOwnPref() {
        WhCategory elektronik = category("Elektronik", null);
        WhCategory laptops    = category("Laptops", elektronik);

        when(repo.findByWhCategory(laptops)).thenReturn(Optional.empty());
        when(repo.findByWhCategory(elektronik))
            .thenReturn(Optional.of(pref("cpu", "ram")));

        assertThat(service.getPreferences(laptops))
            .containsExactly("cpu", "ram");
    }

    @Test
    void getPreferences_inheritsRecursively_acrossThreeLevels() {
        WhCategory root   = category("Elektronik", null);
        WhCategory mid    = category("Computer", root);
        WhCategory leaf   = category("Gaming-Laptops", mid);

        when(repo.findByWhCategory(leaf)).thenReturn(Optional.empty());
        when(repo.findByWhCategory(mid)).thenReturn(Optional.empty());
        when(repo.findByWhCategory(root))
            .thenReturn(Optional.of(pref("cpu", "gpu")));

        assertThat(service.getPreferences(leaf))
            .containsExactly("cpu", "gpu");
    }

    @Test
    void getPreferences_returnsEmpty_whenNoMatchInWholeHierarchy() {
        WhCategory root = category("Elektronik", null);
        WhCategory leaf = category("Laptops", root);

        when(repo.findByWhCategory(any())).thenReturn(Optional.empty());

        assertThat(service.getPreferences(leaf)).isEmpty();
    }

    @Test
    void getPreferences_returnsEmpty_whenCategoryNull() {
        assertThat(service.getPreferences(null)).isEmpty();
    }

    @Test
    void getPreferences_prefersOwnOverParent_whenBothExist() {
        WhCategory parent = category("Elektronik", null);
        WhCategory child  = category("Laptops", parent);

        when(repo.findByWhCategory(child))
            .thenReturn(Optional.of(pref("display", "akku")));
        when(repo.findByWhCategory(parent))
            .thenReturn(Optional.of(pref("cpu", "ram")));

        // Eigene Präferenz gewinnt — Parent wird nicht angefragt
        assertThat(service.getPreferences(child))
            .containsExactly("display", "akku");
        verify(repo, never()).findByWhCategory(parent);
    }

    // --- Max-5-Validierung ---

    @Test
    void setPreferences_accepts_fiveKeywords() {
        WhCategory cat = category("Laptops", null);
        assertThatNoException().isThrownBy(() ->
            service.setPreferences(cat, List.of("cpu", "ram", "display", "akku", "gpu")));
    }

    @Test
    void setPreferences_rejects_moreThanFiveKeywords() {
        WhCategory cat = category("Laptops", null);
        assertThatThrownBy(() ->
            service.setPreferences(cat, List.of("a", "b", "c", "d", "e", "f")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("5");
    }

    @Test
    void setPreferences_accepts_emptyList() {
        WhCategory cat = category("Laptops", null);
        assertThatNoException().isThrownBy(() ->
            service.setPreferences(cat, List.of()));
    }

    // --- getQueryKeywords / getMandatoryFields ---

    @Test
    void getQueryKeywords_returnsFirstFive_whenMoreThanFiveInherited() {
        // Gesamt-Präferenzen können > 5 sein — nur erste 5 für Brave-Query
        WhCategory cat = category("Laptops", null);
        when(repo.findByWhCategory(cat))
            .thenReturn(Optional.of(pref("a", "b", "c", "d", "e", "f", "g")));

        assertThat(service.getQueryKeywords(cat)).hasSize(5);
    }

    @Test
    void getMandatoryFields_returnsAll_regardlessOfCount() {
        WhCategory cat = category("Laptops", null);
        when(repo.findByWhCategory(cat))
            .thenReturn(Optional.of(pref("a", "b", "c", "d", "e", "f", "g")));

        assertThat(service.getMandatoryFields(cat)).hasSize(7);
    }

    @Test
    void getQueryKeywords_returnsEmpty_whenNoPreferences() {
        WhCategory cat = category("Laptops", null);
        when(repo.findByWhCategory(any())).thenReturn(Optional.empty());

        assertThat(service.getQueryKeywords(cat)).isEmpty();
    }
}
```

---

## Prompt H — ProductLookupService (Orchestrierung)

**Setzt voraus:** Prompt A, D, E, F, G

**Aufgabe:**
`ProductLookupService` als zentrale Orchestrierung implementieren
basierend auf Kap. 6, 7, 8, 9.

```
lookup(lookupTerm, whCategory):
  1. Cache-Check [Kap. 6]
     → COMPLETE: return cached quickFactsJson
     → FAILED: return FAILED
     → QUOTA_EXCEEDED: weiter zu Schritt 2
     → nicht vorhanden: weiter zu Schritt 2

  2. Kontingent-Check [Kap. 7]
     → gesperrt: ProductLookup(QUOTA_EXCEEDED) speichern, return
     → frei: weiter zu Schritt 3

  3. Präferenzen holen [Kap. 3]
     → CategorySpecPreferenceService.getQueryKeywords(whCategory)

  4. Brave Search [Kap. 8]
     → BraveSearchService.search(lookupTerm, keywords)
     → Keine Treffer nach Stufe 3: ProductLookup(FAILED) speichern, return

  5. LLM-Verarbeitung [Kap. 9]
     → GroqExtractionService.extractFromSnippets(results, mandatoryFields)

  6. ProductLookup(COMPLETE) speichern
     → quickFactsJson, icecatId, icecatUrl, sourceUrls
```

**JUnit Tests:**

```java
@ExtendWith(MockitoExtension.class)
class ProductLookupServiceTest {

    @Mock ProductLookupRepository repo;
    @Mock QuotaService quotaService;
    @Mock BraveSearchService braveSearchService;
    @Mock GroqExtractionService groqExtractionService;
    @Mock CategorySpecPreferenceService prefService;
    @InjectMocks ProductLookupService service;

    // --- Cache-Logik ---

    @Test
    void lookup_returnsCachedResult_whenComplete() {
        ProductLookup cached = lookup(LookupStatus.COMPLETE, "{\"cpu\":\"i7\"}");
        when(repo.findByLookupTerm("ThinkPad X1")).thenReturn(Optional.of(cached));

        ProductLookupResult result = service.lookup("ThinkPad X1", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.COMPLETE);
        assertThat(result.getQuickFactsJson()).isEqualTo("{\"cpu\":\"i7\"}");
        verifyNoInteractions(quotaService, braveSearchService, groqExtractionService);
    }

    @Test
    void lookup_returnsFailed_whenCachedFailed() {
        when(repo.findByLookupTerm("Unbekanntes Gerät"))
            .thenReturn(Optional.of(lookup(LookupStatus.FAILED, null)));

        ProductLookupResult result = service.lookup("Unbekanntes Gerät", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.FAILED);
        verifyNoInteractions(braveSearchService);
    }

    @Test
    void lookup_continuesAfterCachedQuotaExceeded_whenQuotaFree() {
        // QUOTA_EXCEEDED im Cache → trotzdem Kontingent prüfen (neue Periode?)
        when(repo.findByLookupTerm("ThinkPad"))
            .thenReturn(Optional.of(lookup(LookupStatus.QUOTA_EXCEEDED, null)));
        when(quotaService.checkQuota(Provider.BRAVE)).thenReturn(QuotaStatus.OK);
        when(prefService.getQueryKeywords(any())).thenReturn(List.of());
        when(braveSearchService.search(any(), any())).thenReturn(List.of());

        service.lookup("ThinkPad", null);

        verify(braveSearchService).search(any(), any());
    }

    // --- Kontingent-Logik ---

    @Test
    void lookup_savesQuotaExceeded_whenQuotaFull() {
        when(repo.findByLookupTerm(any())).thenReturn(Optional.empty());
        when(quotaService.checkQuota(Provider.BRAVE)).thenReturn(QuotaStatus.QUOTA_EXCEEDED);

        ProductLookupResult result = service.lookup("ThinkPad X1", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.QUOTA_EXCEEDED);
        verify(repo).save(argThat(l -> l.getLookupStatus() == LookupStatus.QUOTA_EXCEEDED));
        verifyNoInteractions(braveSearchService);
    }

    // --- Brave Search → FAILED ---

    @Test
    void lookup_savesFailed_whenAllBraveStagesEmpty() {
        when(repo.findByLookupTerm(any())).thenReturn(Optional.empty());
        when(quotaService.checkQuota(any())).thenReturn(QuotaStatus.OK);
        when(prefService.getQueryKeywords(any())).thenReturn(List.of());
        when(braveSearchService.search(any(), any())).thenReturn(List.of());

        ProductLookupResult result = service.lookup("Unbekanntes Gerät X999", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.FAILED);
        verify(repo).save(argThat(l -> l.getLookupStatus() == LookupStatus.FAILED));
        verifyNoInteractions(groqExtractionService);
    }

    // --- Vollständiger Happy Path ---

    @Test
    void lookup_savesComplete_onSuccessfulFlow() {
        when(repo.findByLookupTerm(any())).thenReturn(Optional.empty());
        when(quotaService.checkQuota(any())).thenReturn(QuotaStatus.OK);
        when(prefService.getQueryKeywords(any())).thenReturn(List.of("cpu", "ram"));
        when(prefService.getMandatoryFields(any())).thenReturn(List.of("cpu", "ram"));
        when(braveSearchService.search(any(), any()))
            .thenReturn(List.of(braveResult("https://icecat.biz/p/lenovo-12345678.html")));
        when(groqExtractionService.extractFromSnippets(any(), any()))
            .thenReturn(quickFacts("{\"cpu\":\"Core Ultra 7\"}", "12345678"));

        ProductLookupResult result = service.lookup("Lenovo Yoga 7", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.COMPLETE);
        verify(repo).save(argThat(l ->
            l.getLookupStatus()  == LookupStatus.COMPLETE &&
            l.getIcecatId()      .equals("12345678")      &&
            l.getQuickFactsJson().contains("Core Ultra 7")));
    }

    @Test
    void lookup_passesPreferenceKeywords_toBraveSearch() {
        when(repo.findByLookupTerm(any())).thenReturn(Optional.empty());
        when(quotaService.checkQuota(any())).thenReturn(QuotaStatus.OK);
        when(prefService.getQueryKeywords(any())).thenReturn(List.of("cpu", "display"));
        when(braveSearchService.search(any(), any())).thenReturn(List.of());

        service.lookup("ThinkPad", mock(WhCategory.class));

        verify(braveSearchService).search(eq("ThinkPad"), eq(List.of("cpu", "display")));
    }

    private ProductLookup lookup(LookupStatus status, String quickFactsJson) {
        return ProductLookup.builder().lookupStatus(status).quickFactsJson(quickFactsJson).build();
    }
    private BraveResult braveResult(String url) { /* ... */ }
    private QuickFactsResult quickFacts(String json, String icecatId) { /* ... */ }
}
```

---

## Prompt I — IcecatService ✅ DURCHGEFÜHRT

**Setzt voraus:** Prompt A, C

**Aufgabe:**
`IcecatService` implementieren basierend auf Kap. 10.

- `fetchFullSpecs(icecatId)` → `String icecatSpecsJson`
- URL: `live.icecat.biz/api?icecat_id={id}&lang=DE&shopname=openIcecat-live`
- Response vollständig in `ProductLookup.icecatSpecsJson` speichern (kein Extrakt)
- `ApiUsageLog` Eintrag (requestType = SPEC_DETAIL, provider = ICECAT)

**Implementierungsdetails:**
- Verwendet `Provider.ICECAT` (ehemals `GOOGLE`, umbenannt via V29)
- `IcecatFetchResult` record: Komponente `isNotFound` (nicht `notFound` — Namenskonflikt mit statischer Factory-Methode `notFound()`). `ProductLookupController` verwendet `fetch.isNotFound()`.
- Alter `IcecatClient` (GTIN/EAN-basiert) entfernt. `EanSearchClient` und `ResearchController` ebenfalls entfernt.

---

## Prompt J — Backend REST Endpoints

**Setzt voraus:** Prompt H, I

**Aufgabe:**
REST-Endpoints für item-research implementieren.

```
POST /api/listings/{id}/lookup
  → Body: { lookupTerm }
  → ProductLookupService.lookup(lookupTerm, whCategory)
  → Response: { lookupStatus, quickFacts, icecatId }

POST /api/listings/{id}/lookup/full-specs
  → IcecatService.fetchFullSpecs(icecatId)
  → Response: { icecatSpecsJson }

GET /api/usage
  → ApiUsageController: QuotaService.getPeriodStart() + ProviderProperties (keine hardcodierten Perioden)
  → Response: { brave: ProviderUsageDto, groq: ProviderUsageDto, openrouter: ProviderUsageDto, icecat: ProviderUsageDto }
  → ProviderUsageDto: { callsThisPeriod, callsToday, tokensIn, tokensOut, quotaUsage, quotaLimit }

PUT /api/settings/preferences/{categoryId}
  → CategorySpecPreferenceService.setPreferences(...)
  → Body: { fieldKeys: ["cpu", "ram"] }

GET /api/settings/preferences
  → Alle aktiven CategorySpecPreferences
  → Response: Liste mit Kategorie + fieldKeys + Vererbungsinfo
```

---

## Prompt K — Angular: item-research Component

**Setzt voraus:** Prompt J

**Aufgabe:**
`ItemResearchComponent` implementieren basierend auf Kap. 2.

- Suchfeld mit lookupTerm (vorausgefüllt aus Store)
- 5 Zustände: leer, COMPLETE, FAILED, QUOTA_EXCEEDED, loading
- Bevorzugte Felder oben rechts (aus quickFacts + Präferenzen)
- Eingeklappte Gruppen (aus icecatSpecsJson Struktur)
- Stern-Toggle: ☆ → ⭐ mit sofortigem Update
- [Alle Specs laden] Button (nur wenn icecatId vorhanden)
- [↗ Geizhals] Deep-Link: `geizhals.at/?fs=[lookupTerm]`

---

## Prompt L — Angular: ExtractionStore Erweiterung

**Setzt voraus:** Prompt K

**Aufgabe:**
Bestehenden `ExtractionStore` um Spec-Lookup-State erweitern.

- `lookupResults: Map<whListingId, ProductLookupDto>`
- `loadingIds: Set<whListingId>`
- `lookup(whListingId, lookupTerm)` → POST /api/listings/{id}/lookup
- `loadFullSpecs(whListingId)` → POST /api/listings/{id}/lookup/full-specs
- `lookupTerm` in Store halten (editierbar)

---

## Prompt M — Angular: Settings — Usage Monitor

**Setzt voraus:** Prompt J

**Aufgabe:** ✅ DURCHGEFÜHRT
Usage Monitor in Settings implementieren basierend auf Kap. 5.

**Implementierung:**
- GET /api/usage → Daten laden
- Tabelle: Provider | Calls+Heute (merged two-line cell) | Tokens IN | Tokens OUT | Kontingent (GradientProgressBar)
- `GradientProgressBarComponent` (`shared/components/gradient-progress-bar/`): inputs `usage`, `limit`. Farbe via HSL-Interpolation `hsl(120 - pct*1.2, 62%, 40%)` (grün→gelb→rot). Label "X / Y".
- Warning-Threshold: ≥80% von `quotaLimit` (dynamisch, nicht hardcodiert)
- Providers: Brave, Groq, OpenRouter, Icecat
- `ProviderUsage` Interface: `{ callsThisPeriod, callsToday, tokensIn, tokensOut, quotaUsage, quotaLimit }`

---

## Prompt N — Angular: Settings — Kategorie-Präferenzen

**Setzt voraus:** Prompt J

**Aufgabe:**
Kategorie-Präferenzen in Settings implementieren basierend auf Kap. 3 und Kap. 4.

- GET /api/settings/preferences → aktive Präferenzen laden
- Suchfeld für Kategorie-Auswahl
- Aktive Präferenzen als Liste mit Hierarchie
- Stern-Toggle mit Max-5-Validierung:
  `⛔ Max. 5 Keywords für Suchanfrage erreicht`
- PUT /api/settings/preferences/{categoryId} → speichern
- Kind zeigt nur Unterschied zur Elternkategorie

---

## Prompt O — Angular: Settings Refactoring (Expandable Cards)

**Parallelisierbar mit:** Prompt M, N
**Setzt voraus:** Prompt J

**Aufgabe:**
Settings-Seite als Expandable Cards umbauen basierend auf Kap. 4.

- Drei Hauptbereiche: Allgemein, API & Suche, Suchanpassung
- Warning-Icon ⚠️ auf Bereich-Ebene wenn ≥ alert-at-percent
- Warning-Icon auf Provider-Ebene mit Tooltip
- Expandable/Collapsible pro Bereich
