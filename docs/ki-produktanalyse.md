# Automatische KI-Produktanalyse — Konzept & Architektur

KI erkennt den Produktnamen aus dem Inseratstext und ermittelt technische Spezifikationen: Brave Search → LLM-Extraktion → Quick Facts anzeigen.

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

## KI-Ergebnisanzeige (Detailansicht)

Die Ergebnisanzeige im Detailbereich zeigt die vom KI-Lookup ermittelten Produktdaten. Preferred fields (USER-Präferenzen der Kategorie) erscheinen immer zuerst, der Rest alphabetisch.

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

| Feld                | Typ                                            | Notiz                                                                 |
| ------------------- | ---------------------------------------------- | --------------------------------------------------------------------- |
| `lookupTerm`        | String                                         | Cache-Key (normalisiert)                                              |
| `lookupStatus`      | Enum (COMPLETE, FAILED, ERROR, QUOTA_EXCEEDED) | NO_SOURCES nie in DB gespeichert                                      |
| `quickFactsJson`    | TEXT                                           | LLM-Extrakt                                                           |
| `icecatId`          | String (nullable)                              | aus LLM oder URL-Pattern                                              |
| `icecatUrl`         | String (nullable)                              |                                                                       |
| `icecatSpecsJson`   | TEXT (nullable)                                | vollst. Icecat-Response                                               |
| `sourceType`        | VARCHAR(30)                                    | ICECAT/GSMARENA/FLATPANELSHD/GENERIC                                  |
| `sourceDomain`      | VARCHAR                                        | z.B. `gsmarena.com`                                                   |
| `sourceUrl`         | VARCHAR                                        | validierte URL der besten Quelle                                      |
| `featureGroupsJson` | TEXT (nullable)                                | für HTML-Fetch-Quellen; null bei ICECAT/GENERIC                       |
| `lastAccessedAt`    | LocalDateTime (nullable)                       | Zeitstempel des letzten Cache-Hits; null = bisher nur frisch geladen  |

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

`AbstractLlmExtractionClient`: `callLlm()`, `callLlmWithJsonRetry()` (bei ungültigem JSON: einmaliger Retry mit expliziter JSON-Anweisung), `applyIcecatIdSafetyCheck()` (Halluzinations-Schutz).

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

## Bekannte Einschränkungen

### Lokales LLM degradiert die Cache-Effizienz

Der `ProductLookup`-Cache ist ein **reiner String-Match** auf `lookupTerm`. Die Qualität des Caches steht und fällt damit, dass dasselbe Produkt immer denselben normalisierten Term erzeugt.

**API-Modelle (Groq / OpenRouter)** liefern hier konsistente Ergebnisse, weil:

- Sie mit 70B+ Parametern trainiert wurden und damit einen deutlich größeren Sprachmodellkontext mitbringen als lokale Quantisierungen (typisch 3B–8B, INT4/INT8)
- Sie gezielt durch RLHF/DPO auf Instruction-Following optimiert sind — strukturierte JSON-Ausgabe und exakte Produktnamen-Extraktion ist genau das, wofür diese Fine-Tuning-Stufen ausgelegt sind
- Produktnamen werden normalisiert: ein API-Modell gibt konsistent `"Sony WH-1000XM5"` zurück, unabhängig davon ob der Inseratstext `"Sony WH1000XM5 Kopfhörer schwarz wie neu"` oder `"WH-1000XM5 NC-Headset OVP"` enthält

**Lokale Modelle** dagegen können dasselbe Inserat unterschiedlich extrahieren:
- Run 1: `"Sony WH-1000XM5"`
- Run 2: `"Sony WH1000XM5 Schwarz"`
- Run 3: `"WH 1000XM5 Noise Cancelling"`

Jede Variante erzeugt einen eigenen `ProductLookup`-Eintrag, verbraucht einen Brave-API-Call, und kein Eintrag wird jemals wiederverwendet. Der Cache degeneriert zu einem reinen Write-Only-Log.

**Empfehlung:** Für produktiven Einsatz immer `querchecker.api.extraction.active-provider: GROQ` oder `OPENROUTER` verwenden. Lokale Modelle eignen sich allenfalls für die Entwicklung ohne Internetabhängigkeit, aber nicht für zuverlässiges Caching.

---

### Condensed Spec ist nicht Teil des Cache-Keys

Beim ersten Lookup für einen Term extrahiert der `LlmApiExtractionModel` zusätzlich eine `condensedSpec` aus dem Inserat — z.B. `{Speicher: 256GB, Farbe: Schwarz}`. Diese wird als Kontext in den LLM-Prompt injiziert, damit das Modell die richtige Produktvariante findet.

Die `condensedSpec` ist jedoch **nicht Teil des Cache-Keys**. `ProductLookup` ist ausschließlich nach `lookupTerm` indiziert.

**Konkrete Auswirkung:** Wenn zwei Inserate denselben Produktnamen extrahieren, aber verschiedene Varianten beschreiben:
- Inserat A: `"Samsung Galaxy S24 Ultra"` + Speicher 256GB → Brave-Call, LLM-Extrakt, gespeichert
- Inserat B: `"Samsung Galaxy S24 Ultra"` + Speicher 512GB → **Cache-Hit**, bekommt Ergebnis von Inserat A

Das gecachte Ergebnis enthält modellgenerische Specs (Displaygröße, Prozessor, Gewicht) und ist für die meisten Anwendungsfälle korrekt. Variantenspezifische Werte (Speicher, RAM-Konfiguration) können abweichen.

Eine variantengenaue Lösung würde einen zusammengesetzten Cache-Key `(lookupTerm, condensedSpecHash)` erfordern — aktuell nicht implementiert.

---

## Offene Punkte

### Backend

- **Fallback bei transientem Netzwerkfehler**: Netzwerkfehler (z.B. Timeout, Connection refused) bei Groq/OpenRouter landen im generischen `catch (Exception e)` von `ProductLookupService` → `ERROR`-Status mit 10min TTL. Es gibt keinen proaktiven Retry — der User muss nach TTL-Ablauf manuell neu laden. Ein kurzer automatischer Retry (1–2x mit Backoff) wäre sinnvoll, ist aber nicht implementiert.

### Zukunft (Multi-User)

- Userverwaltung, Key-Verwaltung pro User, Kontingent pro User-Key
- `ApiUsageLog` um `userId` erweitern
