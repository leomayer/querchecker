# DL-Extraktion — Architektur-Referenz

Automatische Extraktion von Produktnamen/Modellbezeichnungen aus Willhaben-Inseraten für Kreuzsuchen (Geizhals, Brave etc.).

**Modelle (aktiv):**

- `groq` — Cloud-API via Groq, `execution_order=5` (läuft zuerst, schnell)
- `llama` — Lokales GGUF-Modell via llama.cpp, `execution_order=10`
- `source-model: llama` in `application.yml` → nur llamas Term wird als `suggestedTerm` gesendet (füllt Suchfeld vor)

---

## Entities

### `ItemText` — plattformunabhängiger Text-Speicher

```
ItemText
├── id
├── whListing       ManyToOne (nullable)  ← null nach WH-Löschung
├── source          Enum (WH, ...)
├── title           String  ← HTML-stripped via Jsoup
├── description     String  ← Plain Text, kein Token-Limit
├── contentHash     String  ← SHA256(title + description), Change-Detection
└── fetchedAt       LocalDateTime
```

Bei WH-Inhaltsänderung: **neuer Record** — kein Update. `whListing → ItemText` ist 1:n.
Aktuellster Record via `ORDER BY fetchedAt DESC`.

### `DlModelConfig` — Modell-Konfiguration

```
DlModelConfig
├── id
├── modelName     String (unique) — muss mit ExtractionModel.getName() übereinstimmen
├── modelVersion  String
├── temperature   Float
├── maxTokens     Integer  ← aus DB, nicht hardcodiert
├── source        Enum (HUGGINGFACE, LOCAL, API)
├── localPath     String (nullable)
├── active        Boolean  ← false = deaktiviert, Daten bleiben erhalten
└── executionOrder  Int NOT NULL  ← kein DB-Default, muss in jedem Migration-INSERT gesetzt werden (10, 20, 30…)
```

### `DlExtractionRun` — ein Lauf pro Modell pro ItemText

```
DlExtractionRun
├── id
├── itemText        ManyToOne → ItemText
├── modelConfig     ManyToOne → DlModelConfig
├── prompt          String  ← aufgelöster Prompt (für Reproduzierbarkeit)
├── inputHash       String  ← SHA256(title + description)
├── extractedAt     LocalDateTime
├── status          Enum (INIT, PENDING, DONE, FAILED, NO_IMPLEMENTATION, RE_EVALUATE, CANCELLED)
├── durationMs      Long (nullable)  ← Laufzeit in Millisekunden
├── errorMessage    String (nullable, max 500 Zeichen)
└── createdAt       LocalDateTime
```

**Status-Maschine:**

```
INIT ──→ PENDING → DONE
                 ↘ FAILED
INIT → NO_IMPLEMENTATION  (terminal)
INIT → CANCELLED           (Queue-Overflow)
DONE/PENDING/INIT → RE_EVALUATE → INIT (Term-Cleanup vorher)
```

### `DlExtractionTerm` — Terme pro Run

```
DlExtractionTerm
├── id
├── run             ManyToOne → DlExtractionRun
├── term            String
├── confidence      Float (0.0–1.0)
├── userCorrectedTerm, userCorrectedAt, correctionNote  ← spätere Trainingsdaten
```

### `DlCategoryPrompt` — Prompt je Kategorie und Typ

```
DlCategoryPrompt
├── id
├── whCategory    ManyToOne (nullable)  ← null = Default-Prompt
├── promptType    Enum (PRODUCT_NAME, QUICK_FACTS, HTML_FULL_SPECS)
├── systemPrompt  TEXT (nullable)
├── userPrompt    TEXT
└── updatedAt     LocalDateTime
```

Unique Constraint: `(wh_category_id, prompt_type)`.
Seeder: `DlCategoryPromptSeeder` / `DlCategoryPromptDefinitions`.
Resolver: `DlPromptResolver.resolve(category, promptType)` — rekursiv Elternkategorie → Default.

---

## Service-Verantwortlichkeiten

| Service                  | Verantwortung                                                               |
| ------------------------ | --------------------------------------------------------------------------- |
| `DlOrchestrationService` | Duplikat-Check, Prompt auflösen, Run anlegen, sequenziell einreihen         |
| `DlExtractionService`    | Context aufbauen, Token-Kürzung, `ExtractionModel.extract()`, → Persistence |
| `DlFilterService`        | confidence↓ → top-k → min-confidence (VOR DB-Insert)                        |
| `DlPersistenceService`   | Terms speichern, `durationMs` setzen, Status DONE/FAILED, Event publishen   |
| `DlPromptResolver`       | Kategorie-spezifisch → Eltern → Default                                     |
| `DlCategoryPromptSeeder` | Idempotentes Befüllen von `DlCategoryPrompt`                                |
| `LlmApiExtractionModel`  | Delegiert an `ExtractionProviderRouter` (Groq oder OpenRouter), Length-Guard 150 Zeichen |

---

## Modell-Registrierung (`DlModelConfiguration`)

- Modelle sind **NICHT** als `@Component` Beans registriert
- `DlModelConfiguration` mit `@EventListener(ApplicationReadyEvent.class)` registriert Modelle NACH Context-Initialisierung
- **API-Mode** (`querchecker.llm.mode=API`): nur `LlmApiExtractionModel` wird als Singleton registriert
- **LOCAL-Mode** (`querchecker.llm.mode=LOCAL`): `findByActiveTrueOrderByExecutionOrderAsc()` aus DB, nur aktive Modelle registriert
- `DlOrchestrationService` nutzt `ObjectProvider<List<ExtractionModel>>` für lazy Resolution
- **Vorteil**: keine GGUF-Dateien geladen, wenn Modelle nicht aktiv/konfiguriert

## Queue-Architektur (`DlOrchestrationService`)

- `LinkedBlockingDeque<Runnable>` (unbounded) + `ThreadPoolExecutor(1,1)` — global strikt sequenziell
- `addFirst()` nach DESC `executionOrder` sort → niedrigste executionOrder läuft zuerst
- **Queue-Limit**: aus `AppConfig("dl.queue.limit")`, default 10. Nur wartende Tasks, aktiver zählt nicht.
- **Overflow**: `pollLast()` → niedrigste Priorität raus → `ExtractionTask` setzt CANCELLED + speichert
- **Duplicate-Check**: `existsByItemTextAndModelConfigAndStatusIn([DONE, INIT, PENDING])` → skip. CANCELLED wird nicht geskippt → Retry möglich.

---

## Gesamtfluss

```
WhSearchService.upsertListing()
    → ItemText anlegen / wiederverwenden
    → DlOrchestrationService.scheduleExtraction(itemText)
        ├── aktive DlModelConfig aus DB (nach executionOrder sortiert)
        ├── Duplikat-Check (DONE/INIT/PENDING → skip)
        ├── DlPromptResolver.resolve(category, PRODUCT_NAME) → prompt
        ├── Component-Check: ExtractionModel für modelName vorhanden?
        │       → NEIN: Run mit NO_IMPLEMENTATION anlegen (terminal)
        │       → JA:   Run mit INIT anlegen
        └── per ExtractionTask in Queue einreihen (addFirst = höchste Prio zuerst)

DlExtractionService.runModel(run)
    ├── status = PENDING
    ├── Context = title + "\n\n" + description, gekürzt auf maxTokens
    ├── ExtractionModel.extract() → terms
    └── DlPersistenceService.saveResults()
            → DlFilterService.filter()
            → DlExtractionTermRepository.saveAll()
            → status = DONE / FAILED
            → ApplicationEventPublisher.publishEvent(DlExtractionCompletedEvent)
                ← feuert nach JEDEM Modell einzeln

DlExtractionController.onExtractionCompleted(event)
    ├── WhItemRepository → whItemId auflösen
    ├── Terms aus DB lesen
    └── SseHub.broadcast("dl-extract", { whItemId, terms, suggestedTerm? })
```

---

## Konfiguration (`application.yml`)

```yaml
querchecker:
  dl:
    min-confidence: 0.0 # Evaluierungsphase — später 0.65
    top-k: 5 # Filter VOR DB-Insert
    source-model: llama # Nur dieses Modell sendet suggestedTerm im SSE

  api:
    extraction:
      active-provider: GROQ # GROQ | OPENROUTER — Neustart bei Wechsel
```

---

## DevTools-Hinweis

`src/main/resources/META-INF/spring-devtools.properties` schließt `llama-.*\.jar` vom RestartClassLoader aus — verhindert `UnsatisfiedLinkError` auf `libjllama.so` bei Hot-Restarts.
