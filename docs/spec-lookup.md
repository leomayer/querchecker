# Spec-Lookup & Item Research — Konzept & Architektur

Technische Spezifikationen zu einem Inserat laden: Brave Search → LLM-Extraktion → Quick Facts anzeigen.

---

## Gesamtflow

```
Inserat öffnen → DL-Extraktion → Produktname als lookupTerm vorschlagen
    ↓
item-research: Suchfeld (editierbar, vorausgefüllt)
    ↓
[Specs laden] geklickt
    → Cache-Check (ProductLookup per lookupTerm)
    → Kontingent-Check
    → Multi-Source-Loop (CategorySearchSource je Kategorie)
        → Brave Search
        → HTML-Fetch (GSMARENA/FLATPANELSHD) oder Snippets (ICECAT/GENERIC)
        → LLM-Extraktion → Quick Facts
        → Quality-Check (GOOD/PARTIAL/EMPTY) → Fallback auf nächste Quelle
    → Ergebnis anzeigen
    ↓ (optional, nur bei ICECAT-Quelle)
[Alle Specs laden]
    → Icecat-API direkt → vollständige Feature-Gruppen
```

---

## UI: item-research

### Zustände

| Zustand               | Anzeige                                                                   |
| --------------------- | ------------------------------------------------------------------------- |
| Noch keine Specs      | Suchfeld + [Laden] + [↗ Geizhals]                                         |
| Loading               | Spinner                                                                   |
| Quick Facts vorhanden | Tabelle (preferred fields zuerst) + Quellenangabe + Geizhals-Link         |
| HTML-Fetch-Quelle     | + Specs-Accordion (GSMArena/FlatpanelsHD Feature-Gruppen) direkt sichtbar |
| ICECAT-Quelle         | + [Alle Specs] Button → Icecat-Accordion nachladbar                       |
| FAILED                | ⚠️ "Keine Specs gefunden" — Term editierbar (Retry nach TTL-Ablauf)       |
| ERROR                 | ⚠️ "Fehler beim Laden" — Term editierbar (Retry nach TTL-Ablauf)          |
| NO_SOURCES            | ℹ️ "KI-Suche nicht konfiguriert" — kein Laden-Button (Placeholder)        |
| QUOTA_EXCEEDED        | 🚫 "Kontingent erschöpft bis [Datum]"                                     |

### Computed Signals (`item-research.component.ts`)

`state`, `termGroups`, `lookupState`, `orderedQuickFacts`, `quickFactsRows`, `lookupIcecatId`, `showFullSpecsButton`, `lookupTerm`, `lookupSourceDomain`, `lookupSourceUrl`, `geizhalUrl`, `fullSpecsLoading`, `fullSpecsLoaded`, `icecatFeatureGroups`, `icecatGeneralInfo`, `specsFeatureGroups`, `noIcecatData`, `icecatPageUrl`, `icecatMismatch`, `activeCategoryId`, `preferredKeySet`, `searchButtonDisabled`

- **`orderedQuickFacts`**: preferred fields (aus `preferredKeySet`) zuerst, Rest alphabetisch
- **`showFullSpecsButton`**: nur wenn `icecatId != null && sourceType === 'ICECAT'`

---

## Suchquellen (Multi-Source-Loop)

Konfiguriert per Kategorie via `CategorySearchSource` (DB-Tabelle). Reihenfolge: `priority ASC`.

| SourceType     | Suchweg                          | LLM-Methode                   | UI-Ausgabe                                |
| -------------- | -------------------------------- | ----------------------------- | ----------------------------------------- |
| `ICECAT`       | Brave Snippets (site:icecat.biz) | `extractQuickFacts()`         | Quick Facts + optionaler Icecat-Accordion |
| `GSMARENA`     | HTML-Fetch (Jsoup)               | `extractQuickFactsFromText()` | Quick Facts + Specs-Accordion direkt      |
| `FLATPANELSHD` | HTML-Fetch (Jsoup)               | `extractQuickFactsFromText()` | Quick Facts + Specs-Accordion direkt      |
| `GENERIC`      | Brave Snippets                   | `extractQuickFacts()`         | Quick Facts (kein Icecat-Button)          |

Fallback-Logik: bei `PARTIAL` oder `EMPTY` → nächste Quelle in Liste.

### Brave Search: Dreistufige Query-Strategie (ICECAT)

```
Stufe 1: "[lookupTerm] Spezifikationen [pref1..5] site:icecat.biz" -filetype:pdf ...
Stufe 2: "[lookupTerm] Spezifikationen technische Daten site:icecat.biz" -filetype:pdf
Stufe 3: "[lookupTerm] site:icecat.biz" -filetype:pdf
→ FAILED wenn alle 3 Stufen leer
```

Präferenz-Keywords: max. 5 USER-Felder aus `CategorySpecPreference`. Stufen sind interne Details.

---

## Kategorie-Präferenzen

`CategorySpecPreference` + `CategorySpecPreferenceField` — pro Kategorie, vererbbar:

- **`getMandatoryFields()`** → SYSTEM + USER → an LLM als `{mandatoryFields}`
- **`getQueryKeywords()`** → USER-Felder (max. 5) → Brave-Query-Keywords
- **`getMandatorySystemFields()`** → nur SYSTEM → für Quality-Check

`FieldSource.SYSTEM`: benannte Felder (cpu, ram, display…) → quickFacts-Keys
`FieldSource.USER`: Wert-Keywords (OLED, Core i7…) → Brave-Query-Erweiterung, nicht als quickFacts-Keys auswertbar

**Vererbung**: rekursiv Elternkategorie → Großelternkategorie → ... → null (keine Hardcodierung auf n Ebenen).

---

## Extraction Quality

`ExtractionQualityEvaluator.evaluate(QuickFactsResult, systemFields, SourceType)`:

| Wert                 | Bedingung                                                           |
| -------------------- | ------------------------------------------------------------------- |
| `GOOD`               | ≥60% SYSTEM-Felder befüllt (+ icecatId vorhanden bei ICECAT-Quelle) |
| `PARTIAL`            | >0% aber <60%                                                       |
| `EMPTY`              | 0%                                                                  |
| `FAILED_NO_CRITERIA` | keine SYSTEM-Felder konfiguriert                                    |

---

## Cache-Logik (`ProductLookup`)

```
lookupTerm → DB-Lookup:
COMPLETE       → quickFacts sofort anzeigen (kein API-Call) — permanent
FAILED         → kein API-Call, solange TTL läuft (default 24h); danach erneute Suche
                 konfigurierbar: AppConfig key "product.lookup.failed.ttl.hours"
ERROR          → kein API-Call, solange TTL läuft (default 10min); danach erneute Suche
                 konfigurierbar: AppConfig key "product.lookup.error.ttl.minutes"
QUOTA_EXCEEDED → weiter zu Kontingent-Check (wird überschrieben wenn Periode neu)
NO_SOURCES     → kein DB-Eintrag — jeder Aufruf prüft die Quellen neu (virtual status)
nicht vorhanden → Kontingent-Check → Quellen laden → Brave → LLM
```

`COMPLETE` hat kein TTL — Specs ändern sich nach Produktrelease nicht. Löschen nur via Settings-Bereinigung.

**`NO_SOURCES`** tritt auf wenn:

- Kategorie des Listings hat keine `CategorySearchSource`-Einträge (oder alle mit `lookupEnabled=false`)
- Listing hat keine Kategorie (`WhListing.whCategory == null`)

Da keine Quellen konfiguriert sind, macht ein Cachen keinen Sinn — beim nächsten Aufruf wird die Konfiguration erneut geprüft.

---

## Kontingent-Logik

```
< 80%   → normal → Suche
≥ 80%   → ⚠️ Warning-Icon in Settings → Suche läuft noch
= 100%  → 🚫 gesperrt → QUOTA_EXCEEDED anlegen, kein API-Call
```

---

## Datenstruktur

### `ProductLookup` (Entity)

| Feld                | Typ                                            | Notiz                                           |
| ------------------- | ---------------------------------------------- | ----------------------------------------------- |
| `lookupTerm`        | String                                         | Cache-Key (normalisiert)                        |
| `lookupStatus`      | Enum (COMPLETE, FAILED, ERROR, QUOTA_EXCEEDED) | NO_SOURCES nie in DB gespeichert                |
| `quickFactsJson`    | TEXT                                           | LLM-Extrakt                                     |
| `icecatId`          | String (nullable)                              | aus LLM oder URL-Pattern                        |
| `icecatUrl`         | String (nullable)                              |                                                 |
| `icecatSpecsJson`   | TEXT (nullable)                                | vollst. Icecat-Response                         |
| `sourceType`        | VARCHAR(30)                                    | ICECAT/GSMARENA/FLATPANELSHD/GENERIC            |
| `sourceDomain`      | VARCHAR                                        | z.B. `gsmarena.com`                             |
| `sourceUrl`         | VARCHAR                                        | validierte URL der besten Quelle                |
| `featureGroupsJson` | TEXT (nullable)                                | für HTML-Fetch-Quellen; null bei ICECAT/GENERIC |

### `CategorySearchSource` (Entity, V30)

| Feld                | Typ                                               |
| ------------------- | ------------------------------------------------- |
| `whCategory`        | ManyToOne (nullable = default)                    |
| `priority`          | int                                               |
| `siteDomain`        | String                                            |
| `sourceType`        | SourceType                                        |
| `queryExcludes`     | `List<String>` (TEXT[])                           |
| `searchResultCount` | int (10=snippets, 3=HTML-fetch)                   |
| `lookupEnabled`     | boolean                                           |
| `inheritFromParent` | boolean (true = level-1 als Fallback für level-2) |

Seeders: `CategorySearchSourceDefinitions` + `CategorySearchSourceSeeder` — 13 Kategorien vorkonfiguriert.

---

## LLM-Architektur

```
ExtractionClient (Interface)
├── extractProductName(title, description, categoryName, prompt)
├── extractQuickFacts(lookupTerm, categoryName, braveResults, mandatoryFields, prompt)
└── extractQuickFactsFromText(lookupTerm, categoryName, pageText, mandatoryFields, prompt)

Implementierungen:
├── GroqExtractionClient
└── OpenRouterExtractionClient

ExtractionProviderRouter → aktiver Provider via querchecker.api.extraction.active-provider
```

`AbstractLlmExtractionClient`: `callLlm()`, JSON-Parsing, `applyIcecatIdSafetyCheck()` (Halluzinations-Schutz).

---

## API-Konfiguration (`application.yml`)

```yaml
querchecker:
  api:
    extraction:
      active-provider: GROQ # GROQ | OPENROUTER
    providers:
      brave:
        free-limit: 1000
        free-limit-period: MONTHLY
        period-start-day: 1
        alert-at-percent: 80
      groq:
        model: llama-3.1-8b-instant
        free-limit: 25000
        free-limit-period: DAILY
        alert-at-percent: 80
      openrouter:
        model: meta-llama/llama-3.3-70b-instruct:free
        free-limit: 0
        free-limit-period: DAILY
        alert-at-percent: 80
```

API-Keys in `secret.yml` (nicht in Git): `querchecker.api.providers.{brave,groq,openrouter}.api-key`.

---

## Caching & Rechtliches

| Quelle      | Raw-Response cachen | LLM-Extrakt cachen |
| ----------- | ------------------- | ------------------ |
| Brave       | ❌ ToS-Verstoß      | ✅ erlaubt         |
| Groq        | —                   | ✅ eigenes Werk    |
| Icecat Open | ✅ explizit erlaubt | ✅                 |

---

## Offene Punkte

### Frontend

- **`NO_SOURCES`-Zustand behandeln**: `item-research` zeigt noch keinen Placeholder für `NO_SOURCES` — muss analog zu "KI-Suche deaktiviert" implementiert werden (kein Spinner, kein Error, neutraler Hinweis "KI-Suche nicht konfiguriert"). Status kommt vom Backend wenn Kategorie keine Quellen hat.
- **`ERROR`-Zustand behandeln**: Analog zu FAILED anzeigen, aber mit anderem Text ("Fehler beim Laden").
- **Kategorie-Präferenzen Settings** (`settings/kategorie-praeferenzen/`): UI zur Verwaltung von SYSTEM/USER Feldern pro Kategorie. Max. 5 USER-Keywords als Limit-Guard. Vererbung (Elternkategorie) anzeigen.
- **Settings als Expandable Cards**: Refactoring der Settings-Route — aktuelle Struktur durch collapsible Material-Cards ersetzen.

### Backend

- **Fallback bei Groq-Netzwerkfehler**: Kein Retry implementiert — Exception führt zu leerem Extraktionsergebnis. Ggf. einfaches Retry mit kurzer Wartezeit.
- **Fehlerfall: LLM liefert kein valides JSON**: Aktuell: FAILED. Optional: Retry-Prompt mit expliziterer Anweisung.

### Zukunft (Multi-User)

- Userverwaltung, Key-Verwaltung pro User, Kontingent pro User-Key
- `ApiUsageLog` um `userId` erweitern
