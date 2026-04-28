# Automatische KI-Produktanalyse — Konzept & Architektur

**Zweck:** Dieses Dokument beschreibt die KI-Suche-Pipeline — von einem erkannten Produktnamen bis zur Anzeige technischer Spezifikationen. Es deckt ab: Gesamtflow, LLM-Architektur, Suchquellen-Strategie, Kategorie-Präferenzen, Extraktionsqualität, Caching (Rechtliches), UI-Zustände in der Detailansicht sowie eine bekannte Einschränkung beim Cache-Key-Design. Die vorgelagerte Extraktion des Produktnamens aus dem Inseratstext ist nicht Teil dieses Dokuments.

> Wie der Produktname aus dem Inseratstext extrahiert wird (Queue, Modelle, Status-Maschine): ⚙️ [Extraction Engine](extraction-engine.md).

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
        → Brave Search / Google Discovery
        → HTML-Fetch (GSMARENA/FLATPANELSHD) oder Snippets (ICECAT/GENERIC)
        → LLM-Extraktion → Quick Facts
        → Quality-Check (GOOD/PARTIAL/EMPTY) → Fallback auf nächste Quelle
    → Ergebnis anzeigen
    ↓ (optional, nur bei ICECAT-Quelle)
[Alle Specs laden]
    → Icecat-API direkt → vollständige Feature-Gruppen
```

---

## LLM-Architektur

```
ExtractionClient (Interface)
├── extractProductName(...)
├── extractQuickFacts(..., int sourceIndex)
└── extractQuickFactsFromText(..., int sourceIndex)

AbstractLlmExtractionClient
├── callLlm(...)           — Modell pro Call (nicht aus getModel())
├── callLlmWithJsonRetry() — einmaliger Retry bei ungültigem JSON
├── getModelForLookup(int) — Hook für Modellwahl je sourceIndex
└── applyIcecatIdSafetyCheck() — Halluzinations-Schutz für icecatId

GroqExtractionClient       — überschreibt getModelForLookup (dual-model)
OpenRouterExtractionClient — einzelnes Modell

ExtractionProviderRouter → aktiver Provider via querchecker.llm.external-provider
```

Wesentliche Designentscheidungen die nicht aus dem Code ersichtlich sind:

- **Modell wird pro Call übergeben** (nicht aus `getModel()`), damit derselbe Client-Singleton unterschiedliche Modelle pro Anfrage nutzen kann — Voraussetzung für die Dual-Model-Strategie.
- **Rate-Limit-Logging**: Bei HTTP 429 werden die _geschätzten_ Input-Tokens (vor dem Call berechnet) geloggt, damit der Kontingentverbrauch auch für fehlgeschlagene Requests sichtbar ist.

### Groq Dual-Model-Strategie (Groq Free Tier)

**Hintergrund:** Das Groq Free Tier begrenzt Token-Verbrauch _pro Modell_ getrennt. Jede konfigurierte Kategorie kann mehrere Suchquellen haben (z.B. Icecat + GSMArena + FLATPANELSHD). Wenn die erste Quelle kein `GOOD`-Ergebnis liefert, werden die Folge-Quellen ebenfalls abgefragt — und aufbereitet jeweils mit einem eigenen LLM-Call. Schon bei einem Inserat kann sich die Abfolge der Abfragen zusummieren, sodass das Rate-Limit überschritten wird. Da das Limit pro Modell gilt, lässt sich der Gesamtverbrauch auf zwei Modelle verteilen: das kleine Modell übernimmt den ersten (häufigsten) Lookup, das große Modell die selteneren Folge-Quellen.

| Anwendungsfall                               | Modell                    | Begründung                                                                    |
| -------------------------------------------- | ------------------------- | ----------------------------------------------------------------------------- |
| DL-Extraktion (Produktname)                  | `llama-3.1-8b-instant`    | Schnell, strukturiertes JSON, eigenes Modell-Limit                            |
| QuickFacts — 1. Quelle (`sourceIndex=0`)     | `llama-3.1-8b-instant`    | Häufigster Call — schont großes Modell-Limit                                  |
| QuickFacts — Folge-Quellen (`sourceIndex>0`) | `llama-3.3-70b-versatile` | Nur bei Fallback nötig — bessere Extraktionsqualität, zusätzliches Rate-Limit |

---

## Suchquellen (Multi-Source-Loop)

Konfiguriert per Kategorie via `CategorySearchSource` (DB-Tabelle). Reihenfolge: `priority ASC`.

> Für den Trade-off-Vergleich zwischen Brave und Google Discovery (warum zwei Provider, Vor-/Nachteile): 🏗️ [Architecture — Provider-Agnostic Design](../architecture.md#provider-agnostic-design).

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

### Google Discovery: Besonderheiten gegenüber Brave

- **Term in Anführungszeichen** — Exact-Match-Präzision für Produktnamen
- **Locale-Deduplizierung** — Sites wie icecat.biz liefern dasselbe Produkt in vielen Sprachvarianten (`/de/p/`, `/us/p/`, …); Duplikate werden per kanonisiertem URL-Pfad gefiltert
- **Snippet-Struktur** — Discovery Engine liefert Snippets als Liste von Structs, nicht als einfachen String wie Brave
- **Result-Count** — `pageSize` wird von der SDK-Methode ignoriert; Begrenzung erfolgt manuell im Code

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

Das Ergebnis wird nach jedem LLM-Call auf DEBUG-Level geloggt: `[ProductLookupService] Extraction quality for ICECAT (icecat.biz): PARTIAL — 3 quickFacts extracted`.

---

## Caching

### Cache-Verhalten & Limits

> Vollständige Tabelle mit Status, TTL-Konfiguration und Fallback-Verhalten: 🛡️ [Robustness — Cache & TTL Strategy](robustness.md#cache--ttl-strategy)

### Rechtliches

| Quelle      | Raw-Response cachen | LLM-Extrakt cachen |
| ----------- | ------------------- | ------------------ |
| Brave       | ❌ ToS-Verstoß      | ✅ erlaubt         |
| Groq        | —                   | ✅ eigenes Werk    |
| Icecat Open | ✅ explizit erlaubt | ✅                 |

---

## KI-Ergebnisanzeige (Detailansicht)

Die Ergebnisanzeige im Detailbereich zeigt die vom KI-Lookup ermittelten Produktdaten. Preferred fields (USER-Präferenzen der Kategorie) erscheinen immer zuerst, der Rest alphabetisch.

| Zustand                | Anzeige                                                                    |
| ---------------------- | -------------------------------------------------------------------------- |
| Noch keine Details     | Suchfeld + [Laden] + [↗ Geizhals]                                          |
| Loading                | Spinner                                                                    |
| Quick Facts vorhanden  | Tabelle (preferred fields zuerst) + Quellenangabe + Geizhals-Link          |
| HTML-Fetch-Quelle      | + Feature-Gruppen-Accordion (GSMArena/FlatpanelsHD) direkt sichtbar        |
| ICECAT-Quelle          | + [Alle Details] Button → Icecat-Accordion nachladbar                      |
| FAILED                 | ⚠️ "Keine Details gefunden" — Term editierbar (Retry nach TTL-Ablauf)      |
| ERROR                  | ⚠️ "Fehler beim Laden" — Term editierbar (Retry nach TTL-Ablauf)           |
| NO_SOURCES             | ℹ️ "KI-Suche nicht konfiguriert" — kein Laden-Button (Placeholder)         |
| QUOTA_EXCEEDED         | 🚫 "Kontingent erschöpft bis [Datum]"                                      |

---

## Usage Monitor (Settings)

Zeigt den aktuellen Verbrauch aller konfigurierten Provider — erkennbar ob das Kontingent knapp wird, wie viele Rate-Limit-Hits aufgetreten sind, und wie sich der Token-Verbrauch auf die konfigurierten Modelle verteilt. Relevant für die Entscheidung, ob Schwellenwerte angepasst oder Quellen temporär deaktiviert werden sollten.

Tabelle: `Provider | Calls | Tokens IN | OUT | 429 | Kontingent | Zeitraum`

- **429-Spalte**: Anzahl Rate-Limit-Hits im aktuellen Zeitraum. Tooltip zeigt geschätzte Input-Tokens aus rate-limiteten Calls.
- **Modell-Aufschlüsselung**: Bei aktivem Groq-Provider werden unter dem Groq-Aggregat-Eintrag Zeilen pro konfiguriertem Modell eingeblendet (primär + sekundär), jeweils mit eigenen Call/Token-Zählungen.

---

## Bekannte Einschränkung: Condensed Spec ist nicht Teil des Cache-Keys

Beim ersten Lookup für einen Term extrahiert der `LlmApiExtractionModel` zusätzlich eine `condensedSpec` aus dem Inserat — z.B. `{Speicher: 256GB, Farbe: Schwarz}`. Diese wird als Kontext in den LLM-Prompt injiziert, damit das Modell die richtige Produktvariante findet.

Die `condensedSpec` ist jedoch **nicht Teil des Cache-Keys**. `ProductLookup` ist ausschließlich nach `lookupTerm` indiziert.

**Konkrete Auswirkung:** Wenn zwei Inserate denselben Produktnamen extrahieren, aber verschiedene Varianten beschreiben:

- Inserat A: `"Samsung Galaxy S24 Ultra"` + Speicher 256GB → Brave-Call, LLM-Extrakt, gespeichert
- Inserat B: `"Samsung Galaxy S24 Ultra"` + Speicher 512GB → **Cache-Hit**, bekommt Ergebnis von Inserat A

Das gecachte Ergebnis enthält modellgenerische Specs (Displaygröße, Prozessor, Gewicht) und ist für die meisten Anwendungsfälle korrekt. Variantenspezifische Werte (Speicher, RAM-Konfiguration) können abweichen.

Eine variantengenaue Lösung würde einen zusammengesetzten Cache-Key `(lookupTerm, condensedSpecHash)` erfordern — aktuell nicht implementiert.
