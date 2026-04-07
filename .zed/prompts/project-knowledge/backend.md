# Projektwissen: Querchecker — Backend

## Package-Struktur

```
at.querchecker/
├── entity/         WhListing, WhListingDetail, WhCategory, WhLocation, AppConfig
├── dto/            QuercheckerListingDto, WhListingDetailDto, WhSearchResultDto,
│                   WhCategoryDto, WhLocationDto, WhMetaStatusDto,
│                   WhDetailDto, DlExtractionTermDto, DlExtractionDonePayload,
│                   DlExtractionStatusResponse
├── controller/     WhListingController, WhListingDetailController
├── service/        WhListingService, WhListingDetailService, WhItemService
├── repository/     WhListingRepository, WhListingDetailRepository,
│                   WhCategoryRepository, WhLocationRepository, AppConfigRepository,
│                   WhItemRepository
├── config/         CorsConfig, RestTemplateConfig, SpringDocConfig,
│                   UserAgentHolder, UserAgentFilter, RequestUserAgentResolver
├── sse/            SseController, SseHub
├── api/
│   ├── entity/     ApiUsageLog, Provider (enum: BRAVE, GROQ, OPENROUTER, ICECAT), RequestType (enum)
│   ├── exception/  RateLimitException (retryAfterSeconds, provider; parseRetryAfter() statisch)
│   ├── extraction/ ExtractionClient (interface), AbstractLlmExtractionClient,
│   │               GroqExtractionClient, OpenRouterExtractionClient,
│   │               ExtractionProviderRouter
│   ├── model/      ChatRequest, ChatResponse
│   └── service/    ApiUsageLogService, QuotaService
├── wh/             WhSearchController, WhSearchService, WhMetaController,
│                   WhCategoryService, WhLocationService, WhRefreshScheduler,
│                   api/WhApiResponse.java
├── research/
│   ├── entity/     CategorySpecPreference, CategorySpecPreferenceField, ProductLookup,
│   │               LookupStatus (enum: COMPLETE, FAILED, QUOTA_EXCEEDED, NO_SOURCES, ERROR,
│   │                             RATE_LIMITED [virtuell, nie in DB]),
│   │               FieldSource (enum: SYSTEM, USER),
│   │               CategorySearchSource, SourceType (enum: ICECAT, FLATPANELSHD, GSMARENA, GENERIC),
│   │               ExtractionQuality (enum: GOOD, PARTIAL, EMPTY, FAILED_NO_CRITERIA)
│   ├── model/      BraveApiResponse, QuickFactsResult (incl. featureGroups),
│   │               LookupRequest/Response (+ retryAfterSeconds), FullSpecsRequest/Response,
│   │               ProductLookupResult (+ retryAfterSeconds, rateLimited() factory),
│   │               LookupResultPayload (SSE-Event für lookup-result),
│   │               SearchResult (generic: title, url, description, extraSnippets)
│   ├── repository/ CategorySpecPreferenceRepository, CategorySpecPreferenceFieldRepository,
│   │               ProductLookupRepository, CategorySearchSourceRepository
│   ├── config/     ResearchConfig
│   ├── seeder/     CategorySearchSourceDefinitions, CategorySearchSourceSeeder,
│   │               CategorySpecPreferenceSeeder, CategorySpecPreferenceDefinitions
│   ├── SearchResultCacheService (In-Memory ConcurrentHashMap, Key=lookupTerm|sourceDomain)
│   └── services:   WebSearchService (interface), BraveWebSearchService,
│                   GoogleDiscoveryWebSearchService, ProductLookupService,
│                   IcecatService, CategorySpecPreferenceService, CategorySearchSourceService,
│                   ExtractionQualityEvaluator, UrlValidator, HtmlFetchService
│       controllers: ProductLookupController
└── deepLearning/
    ├── entity/     DlModelConfig, DlExtractionRun, DlExtractionTerm, ItemText, WhItem
    ├── repository/ DlModelConfigRepository, DlExtractionRunRepository,
    │               DlExtractionTermRepository, ItemTextRepository, WhItemRepository
    ├── service/    DlOrchestrationService, DlExtractionService, DlPersistenceService,
    │               DlPromptResolver, DlCategoryPromptSeeder, DlFilterService,
    │               ExtractionTask, ItemTextService, ItemTextCleanupScheduler,
    │               KeywordExtractionService, TokenAnalyzer
    ├── extraction/ ExtractionModel (interface), AbstractExtractionModel,
    │               AbstractLlamaExtractionModel, LlmApiExtractionModel,
    │               Llama32ExtractionModel, MdebertaExtractionModel,
    │               NuExtractExtractionModel, NuExtract15ExtractionModel, Qwen25ExtractionModel
    ├── controller/ DlExtractionController
    └── DlCategoryPromptDefinitions (Konstanten für alle Prompts)
```

---

## Entities (alle mit Lombok: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)

### `WhListing` — Kerndata vom Willhaben-Inserat

| Feld           | Typ                                   |
| -------------- | ------------------------------------- |
| `id`           | Long (PK)                             |
| `whId`         | String (unique, Willhaben-interne ID) |
| `title`        | String                                |
| `description`  | String                                |
| `price`        | BigDecimal                            |
| `location`     | String                                |
| `url`          | String                                |
| `thumbnailUrl` | String (nullable)                     |
| `listedAt`     | LocalDateTime                         |
| `fetchedAt`    | LocalDateTime                         |

### `WhListingDetail` — User-Annotationen (1:1 zu WhListing, lazy-created)

| Feld           | Typ                      |
| -------------- | ------------------------ |
| `id`           | Long (PK)                |
| `whListing`    | ManyToOne → WhListing    |
| `note`         | String (nullable)        |
| `viewCount`    | Integer (default 0)      |
| `lastViewedAt` | LocalDateTime (nullable) |
| `rating`       | Enum UP/DOWN/null        |
| `createdAt`    | LocalDateTime            |
| `updatedAt`    | LocalDateTime            |

### `WhCategory` — Hierarchischer Kategoriebaum (3 Ebenen)

| Feld     | Typ                                  |
| -------- | ------------------------------------ |
| `id`     | Long (PK)                            |
| `whId`   | String (Willhaben ATTRIBUTE_TREE ID) |
| `name`   | String                               |
| `level`  | Integer (0=root, 1=sub, 2=sub-sub)   |
| `parent` | ManyToOne → WhCategory (nullable)    |

### `WhLocation` — Hierarchischer Standortbaum

| Feld     | Typ                               |
| -------- | --------------------------------- |
| `id`     | Long (PK)                         |
| `areaId` | Integer (Willhaben areaId)        |
| `name`   | String                            |
| `level`  | Integer (0=Bundesland, 1=Bezirk)  |
| `parent` | ManyToOne → WhLocation (nullable) |

### `AppConfig` — Key-Value Konfiguration

| Feld          | Typ           |
| ------------- | ------------- |
| `key`         | String (PK)   |
| `value`       | String        |
| `description` | String        |
| `updatedAt`   | LocalDateTime |

---

## Deep-Learning-Extraction Entities (`deepLearning/entity/`)

### `ItemText` — Normalisierter Inseratstext (Eingabe für ML)

| Feld        | Typ                   |
| ----------- | --------------------- |
| `id`        | Long (PK)             |
| `whListing` | ManyToOne → WhListing |
| `text`      | String                |

### `WhItem` — View-Entität über WhListing (Frontend-ID)

| Feld        | Typ                   |
| ----------- | --------------------- |
| `id`        | Long (PK)             |
| `whListing` | ManyToOne → WhListing |

### `DlModelConfig` — ML-Modell-Konfiguration

| Feld             | Typ                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------- |
| `id`             | Long (PK)                                                                                                           |
| `name`           | String                                                                                                              |
| `active`         | boolean                                                                                                             |
| `executionOrder` | int (INT NOT NULL, kein DB-Default — muss in jeder Migration explizit gesetzt werden, Konvention: Vielfache von 10) |

### `DlExtractionRun` — Ausführungsprotokoll je Modell+Text

| Feld          | Typ                            |
| ------------- | ------------------------------ |
| `id`          | Long (PK)                      |
| `itemText`    | ManyToOne → ItemText           |
| `modelConfig` | ManyToOne → DlModelConfig      |
| `status`      | Enum (VARCHAR)                 |
| `durationMs`  | Long (nullable, wall-clock ms) |
| `createdAt`   | LocalDateTime                  |

### `DlExtractionTerm` — Erkannter Begriff je Run

| Feld         | Typ                         |
| ------------ | --------------------------- |
| `id`         | Long (PK)                   |
| `run`        | ManyToOne → DlExtractionRun |
| `term`       | String                      |
| `confidence` | Double                      |

---

## Repositories

- `WhListingRepository` – `findByWhId(String whId)`
- `WhListingDetailRepository` – `findByWhListingId(Long)`, `findAllSummaries()` (Projection für effiziente Joins)
  - Projection `WhListingDetailSummary`: `getListingId()`, `getNote()`, `getViewCount()`, `getLastViewedAt()`, `getRating()`
- `WhItemRepository` – `findIdByItemTextId(Long itemTextId)` → `Optional<Long>` (resolves ItemText → WhItem.id via Join)
- `WhCategoryRepository` – `findAllByName(String name)` → `List<WhCategory>` (handles duplicate names across levels)
- `WhLocationRepository`, `AppConfigRepository` – Standard JpaRepository
- `DlModelConfigRepository` – `findByActiveTrueOrderByExecutionOrderAsc()`
- `DlExtractionTermRepository` – `findByWhItemId(Long whItemId)` (Join über WhItem → WhListing → ItemText), `findByItemTextIdAndModelName()`

---

## DTO-Mapping

Manuell via Builder im Service — kein Mapping-Framework (kein MapStruct).

- `QuercheckerListingDto` = WhListing-Felder + `hasNote`, `viewCount`, `lastViewedAt`, `rating`
- `WhListingDetailDto` spiegelt `WhListingDetail` vollständig

---

## API Endpoints

### Listings (`WhListingController`)

- `GET /api/listings` — alle Listings, opt. `ratingFilter` (UP | UP_NULL | DOWN | ALL)
- `GET /api/listings/{id}` — einzelnes Listing
- `POST /api/listings` — erstellen/speichern
- `DELETE /api/listings/{id}` — löschen

### Listing Detail (`WhListingDetailController`)

- `GET /api/listings/{id}/detail` — Detail-Metadaten
- `PUT /api/listings/{id}/detail/note` — Notiz speichern
- `PUT /api/listings/{id}/detail/rating` — Rating setzen (UP/DOWN/null)
- `POST /api/listings/{id}/views` — View-Event aufzeichnen

### Willhaben Search (`WhSearchController`)

- `GET /api/wh/search` — Params: `keyword`, `rows` (default 30), `priceFrom`, `priceTo`, `attributeTree`, `areaId`, `paylivery` (boolean)
- Gibt `WhSearchResultDto` zurück, speichert Ergebnisse in DB (upsert by `whId`)

### Willhaben Meta (`WhMetaController`)

- `GET /api/wh/meta/status` — `WhMetaStatusDto` (lastFetched, refreshInProgress, cron)
- `POST /api/wh/meta/refresh` — Async-Refresh triggern
- `GET /api/wh/meta/categories` — Kategoriebaum
- `GET /api/wh/meta/locations` — Standortbaum

### DL Extraction (`DlExtractionController`)

- `GET /api/dl/extraction/{whItemId}/terms` — `DlExtractionStatusResponse { extractionStatus, terms[], suggestedTerm? }`
  - `{whItemId}` = `WhItem.id` (nicht `ItemText.id`)
  - `suggestedTerm`: bester Term des in `application.yml` konfigurierten `source-model` (Standard: `llama`)

### Spec-Lookup (`ProductLookupController`)

- `POST /api/listings/{listingId}/lookup` — Brave Search + Groq → `LookupResponse { lookupStatus, quickFacts, icecatId }`
- `POST /api/listings/{listingId}/lookup/full-specs` — Icecat API → `FullSpecsResponse { icecatSpecsJson }`

### SSE (`SseController`)

- `GET /api/sse/stream` — Server-Sent Events Stream
- Event `dl-extract`: `DlExtractionDonePayload { whItemId: Long, terms: DlExtractionTermDto[] }`
  - Wird nach jedem Modell-Run gesendet (nicht erst am Ende aller Modelle)
  - `DlExtractionTermDto`: `{ modelName, term, confidence, durationMs }`

Swagger UI: `/swagger-ui.html` (dev only, in Prod via `SPRING_PROFILES_ACTIVE=prod` deaktiviert)

---

## Willhaben-Integration (`wh/`)

- `WhApiResponse.java` — Mapping der inoffiziellen Willhaben JSON-API
- `WhSearchService` — URI bauen, Willhaben aufrufen, Listings upserten, DTOs zurückgeben
  - `buildThumbnailUrl()` — parst MMO-Attribut oder Fallback
  - `buildListingUrl()` — aus SEO_URL-Attribut
  - `upsertListing()` — merge-insert by `whId`
- `WhRefreshScheduler` — geplanter Metadata-Refresh (Kategorien, Standorte)

---

## DL Orchestrierung

- `DlOrchestrationService`: `LinkedBlockingDeque` + `ThreadPoolExecutor(1,1)` — alle Modelle laufen sequenziell, nie parallel
- **Priority Queue**: Tasks sortiert DESC nach `executionOrder`, dann `addFirst()` → niedrigstes executionOrder läuft zuerst
- **Queue Limit**: aus `AppConfig` key `dl.queue.limit` (default 10). Overflow → `pollLast()` → Task auf `CANCELLED` gesetzt
- **CANCELLED Retry**: CANCELLED-Runs werden NICHT übersprungen → neue INIT-Runs bei nächstem Schedule
- Reihenfolge: `DlModelConfig.executionOrder` ASC; Groq hat `executionOrder=5` (läuft zuerst), lokale Modelle 10+
- `DlPersistenceService.saveResults()`: speichert Ergebnis + `durationMs`, publisht `DlExtractionCompletedEvent` nach jedem Modell
- `DlExtractionController.onExtractionCompleted()`: löst `itemTextId → whItemId` auf, sendet SSE mit `suggestedTerm` (aus `source-model`)

## LLM Extraction API (`api/extraction/`)

- `ExtractionClient` interface: `extractProductName(...)` + `extractQuickFacts(..., int sourceIndex)` + `extractQuickFactsFromText(..., int sourceIndex)`. `sourceIndex` = 0-basierter Quellen-Loop-Index, steuert Modellwahl.
- `AbstractLlmExtractionClient`:
  - `callLlm(requestType, lookupTerm, systemPrompt, userPrompt, expectJson, model)` — Modell wird pro Call übergeben
  - `getModelForLookup(int sourceIndex)` — Hook; Standard: immer `getModel()`; Unterklassen überschreiben für Modellwechsel
  - Bei 429: loggt geschätzte Input-Tokens statt null — Rate-Limit-Hits werden in Usage-Statistiken sichtbar
  - `callLlmWithJsonRetry()`, `applyIcecatIdSafetyCheck()`, `formatSnippets()` unverändert
- `GroqExtractionClient`: injiziert `modelLookupSecondary` aus `querchecker.api.limits.groq.model-lookup-secondary`. Überschreibt `getModelForLookup`: `sourceIndex == 0` → primäres Modell (`llama-3.1-8b-instant`); `sourceIndex > 0` → sekundäres Modell (`llama-3.3-70b-versatile`)
- `OpenRouterExtractionClient`: unverändert, nutzt einzelnes Modell
- `ExtractionProviderRouter`: aktiver Provider via `querchecker.llm.external-provider` (GROQ | OPENROUTER)
- `ProviderConfig`: neues Feld `modelLookupSecondary`

## DL Category Prompts

- `DlCategoryPromptDefinitions`: Java-Konstanten für alle Prompts. Enthält `PromptConfig` record `(PromptType, systemPrompt, userPrompt)`. Default-Konstanten: `PRODUCT_NAME_SYSTEM`, `PRODUCT_NAME_USER_DEFAULT`, `QUICK_FACTS_SYSTEM`, `QUICK_FACTS_USER_DEFAULT`. Kategorie-spezifische Prompts in unified `CONFIGS: Map<String, List<PromptConfig>>` (ersetzt die früheren getrennten `*_BY_CATEGORY`-Maps).
- **PRODUCT_NAME**: Reicherer System-Prompt mit nummerierten Regeln, Persona, vielen Beispielen. `condensedSpec`-Keys sind Deutsch, groß geschrieben, mit Leerzeichen getrennt (z.B. "Akku Kapazität", "Bildschirmgröße", "Prozessor"). User-Prompt nur Daten (Kategorie, Titel, Beschreibung). Inch-Mark-Sanitisierungsregel hinzugefügt. Regel 1: wenn `extractedModel` nicht erkennbar → **Feld weglassen** (nicht "UNBEKANNT") — spart Tokens. `extractProductNameStructured` gibt bei erfolgreichem JSON-Parse immer das Ergebnis zurück (auch wenn `extractedModel` fehlt); `null` → `LlmApiExtractionModel` gibt `List.of()` zurück. `max_completion_tokens` = 1024 (war 256).
- **QUICK_FACTS**: System-Prompt mit Konsolidierungsregel, German-Key-Namen (groß geschrieben), Sources-Block mit icecatId-URL-Muster, GB→MB-Normalisierung, Falsch/Richtig-Beispiele. User-Prompt nur Daten. `condensedSpec`-Keys sind Deutsch. Regel 5 (Erscheinungsjahr): "Baujahr aus dem Inserat-Kontext entspricht dem Erscheinungsjahr — verwende stets 'Erscheinungsjahr' als Feldname." (verhindert dass LLM den Inserat-Key direkt übernimmt).
- `DlCategoryPromptSeeder`: Additives Per-Entry-Upsert beim Start — prüft jedes `(Kategorie, PromptType)`-Paar einzeln via `findDefaultByPromptType` + `findByWhCategoryAndPromptType`. Überschreibt niemals vorhandene Einträge. Re-seed: `DELETE FROM dl_category_prompt` + Neustart.
- `DlPromptResolver.resolve(WhCategory, PromptType)`: traversiert Kategoriehierarchie, Fallback auf Default
- **QUICK_FACTS icecatId**: "die rein numerische ID am Ende der icecat-URL, direkt vor .html"
- **AbstractLlmExtractionClient**: Entfernt Füllwerte ("unbekannt", "-", "n/a", "unknown", etc.) aus `quickFacts` nach dem Parsing, damit sie nicht als erfüllte Pflichtfelder in der Quality-Bewertung zählen. Sanitisiert Raw-LLM-Ausgabe vor JSON-Parsing für Inch-Mark-Fehler (Ziffer + nackte Quote → "Ziffer Zoll,").

## Conditional Model Registration (`DlModelConfiguration`)

- Modelle sind **NICHT** als `@Component`-Beans registriert. Stattdessen: `DlModelConfiguration` mit `@EventListener(ApplicationReadyEvent.class)` registriert Modelle NACH vollständiger Context-Initialisierung (Datenbank verfügbar).
- **API-Mode** (`querchecker.llm.mode=API`): nur `LlmApiExtractionModel` wird registriert
- **LOCAL-Mode** (`querchecker.llm.mode=LOCAL`): Datenbankabfrage `DlModelConfigRepository.findByActiveTrueOrderByExecutionOrderAsc()`, nur aktive Modelle als Singletons registriert
- `DlOrchestrationService` nutzt `ObjectProvider<List<ExtractionModel>>` für lazy Dependency-Resolution (statt `@Autowired List`)
- **Vorteil**: Keine unnötige Modell-Initialisierung — lokale GGUF-Dateien werden nicht geladen, wenn Modelle nicht aktiv sind

## LlmApiExtractionModel (`deepLearning/extraction/`)

- Umbenannt von `GroqExtractionModel` — Name war irreführend; delegiert an `ExtractionProviderRouter.getActive()` (Groq **oder** OpenRouter)
- `MODEL_NAME = "groq"` bleibt unverändert für DB-Kompatibilität
- Length-Guard: Terme > 150 Zeichen werden verworfen (verhindert generische Halluzinationen)
- DB: `model_name='groq'`, `source='API'`, `execution_order=5`
- `ModelSource` Enum: `HUGGINGFACE`, `LOCAL`, `API`
- Wird in API-Mode via `DlModelConfiguration` als Singleton registriert

## Research-Package (`research/`)

- `WebSearchService` (Interface): `search(lookupTerm, siteDomain, keywords, queryExcludes, resultCount)` → `List<SearchResult>`. Aktivierung per `querchecker.api.search.active-provider` (default: `BRAVE`).
- `BraveWebSearchService implements WebSearchService`: 3-stufige Suche. Kein `Accept-Encoding: gzip` Header. Ersetzt `BraveSearchService`.
  - **Snippet-Truncation** (2026-03-31): Begrenzt Payload um Token-Overflow zu verhindern (HTTP 413 bei Groq bei >6000 TPM):
    - Top 5 Suchergebnisse (von allen)
    - Descriptions: 250 chars max
    - ExtraSnippets: 7 pro Ergebnis × 250 chars max (war: 2 × 200)
    - Token-Rechnung: ~505 Tokens pro Ergebnis, ~2525 Tokens für 5 Ergebnisse + ~600 Prompt = ~3125 Tokens (unter 6000 Limit mit Spielraum)
    - Implementierung: `truncateString()`, `truncateSnippets()` Hilfsmethoden
- `GoogleDiscoveryWebSearchService implements WebSearchService`: Google Discovery Engine (Cloud-Suche). Wichtige Eigenheiten:
  - **Query-Format**: `"[lookupTerm]" site:[domain] [excludes]` — Term in Anführungszeichen; `queryExcludes` tragen bereits ihr `-` Prefix → kein extra `-` beim Anhängen
  - **Result-Count**: `setPageSize(resultCount)` wird von `iterateAll()` ignoriert (auto-paginiert); Begrenzung erfolgt manuell via Loop-Break in `mapSdkResults()`
  - **Snippet-Extraktion**: `derivedStructData` liefert `"snippets"` als `LIST_VALUE` von Structs (nicht `STRING_VALUE`); `extractSnippets()` iteriert und joined `"snippet"`-Felder
  - **Locale-Deduplizierung**: URL-Pfad wird kanonisiert (Locale-Prefix `/de/`, `/us/`, `/ar-sa/` gestripped via Regex); Duplikate per `HashSet<String> seenCanonicalPaths` gefiltert
  - **Debug**: Keys des ersten `derivedStructData` auf DEBUG geloggt
- `SearchResult` record: `(title, url, description, extraSnippets)` — generisch. Ersetzt `BraveResult`.
  - Hinweis: `extraSnippets` ist **Brave-spezifisch**. Google Discovery befüllt nur `description` (aus `extractSnippets()`), `extraSnippets` bleibt leer.
- `ProductLookupService`: Multi-Source-Schleife über `CategorySearchSource`-Liste mit 0-basiertem `sourceIndex` (Haupt-Loop + Retry-Loop). Je Quelle: Brave → LLM (mit `sourceIndex` für Modellwahl) → Quality-Check (Ergebnis auf DEBUG geloggt) → weiter bei PARTIAL/EMPTY. Speichert `sourceType`, `sourceDomain`, `sourceUrl`, `featureGroupsJson` in `ProductLookup`.
  - Leere Quellen → `NO_SOURCES` (nicht gecacht — wird bei jedem Aufruf neu geprüft)
  - Exception → `ERROR` (gecacht mit TTL 10min, konfigurierbar via `AppConfig key: product.lookup.error.ttl.minutes`)
  - `FAILED`: gecacht mit TTL 24h (konfigurierbar via `AppConfig key: product.lookup.failed.ttl.hours`); nach Ablauf erneute Suche
  - Cache-Semantik: COMPLETE = permanent; FAILED = 24h TTL; ERROR = 10min TTL; NO_SOURCES = nie gecacht; QUOTA_EXCEEDED = erneut geprüft
- `IcecatService`: Icecat-API nach `icecatId` (numerische ID, `icecat_id` Query-Param), gibt `icecatSpecsJson` zurück. Verwendet `Provider.ICECAT`. (`IcecatClient` und `EanSearchClient` entfernt.)
- `IcecatFetchResult` record: Komponente heißt `isNotFound` (nicht `notFound` — Namenskonflikt mit statischer Factory-Methode). Accessor: `fetch.isNotFound()`.
- `CategorySpecPreferenceService`: verwaltet Pflichtfelder je Kategorie.
  **Rekursive Vererbung** via `findWithInheritance(WhCategory)`: läuft die gesamte Elternkette hoch bis ein `CategorySpecPreference`-Eintrag gefunden wird (Level-2 → Level-1 → Level-0 → null). Wenn "Notebooks" keinen Eintrag hat, wird auf "Computer / Tablets" zurückgegriffen, dann auf die Root-Kategorie.
  **Unterschied zu `CategorySearchSource.inheritFromParent`**: nur eine Ebene (Level-2 erbt von Level-1, keine weitere Eskalation).
  **`getMandatoryFields(WhCategory)`** — liefert SYSTEM + USER → LLM `{mandatoryFields}` + Quality-Check.
  **`getQueryKeywords(WhCategory)`** — liefert nur USER-Felder (≤5) → Brave-Query-Keywords.
  **`getMandatorySystemFields(WhCategory)`** — liefert nur SYSTEM-Felder. Übergabe an `ExtractionQualityEvaluator` (USER-Felder verfälschen Coverage — sie sind Wert-Keywords, keine quickFacts-Keys).
- `CategorySearchSourceService`: `findForCategory(WhCategory)` — prüft zuerst eigene Level-2-Einträge (aktiv, nach Priority); falls keine vorhanden, Fallback auf Eltern-Einträge mit `inheritFromParent=true`. Nur eine Ebene (kein weiterer Aufstieg).
- `ExtractionQualityEvaluator`: `evaluate(QuickFactsResult, List<String> systemFields, SourceType)` → `ExtractionQuality` (GOOD/PARTIAL/EMPTY/FAILED_NO_CRITERIA). Coverage ≥60% SYSTEM fields = GOOD; <60% = PARTIAL; 0% = EMPTY; keine Kriterien = FAILED_NO_CRITERIA. ICECAT-Quellen: zusätzlich `icecatId` muss vorhanden sein für GOOD.
- `UrlValidator`: Anti-Halluzination URL-Validierung.
  - `resolveSourceUrl(llmUrl, braveResults)` — prüft LLM-URL gegen reale Brave-Ergebnisse, Fallback auf Top-Brave-URL
  - `resolveIcecatId(llmId, braveResults)` — prüft ob icecatId in einer Brave-URL vorkommt
  - `matchesExpectedPattern(url, SourceType)` — Regex: ICECAT `icecat.biz/p/[name]-[id].html`, GSMARENA `gsmarena.com/[name]-[id].php`, FLATPANELSHD `flatpanelshd.com/[\w\-]+.php` (Bindestriche erlaubt); GENERIC passt immer
- `HtmlFetchService`: `shouldFetchFullPage(SourceType)` (true für FLATPANELSHD/GSMARENA). `fetchAndExtract(url, SourceType)`: Jsoup-Fetch mit 10s Timeout, site-spezifische CSS-Selektoren (GSMArena: `table.specs-phone-big-table`; FlatpanelsHD: `table.specsTable, div.specs, table.tv-specs` mit `main`-Fallback).
- `UserAgentHolder`: erfasst ersten Browser-User-Agent aus eingehenden Requests; Fallback: hardcodierter Chrome-UA. `UserAgentFilter`: Jakarta Servlet Filter; speichert UA bei jedem Request in `UserAgentHolder`.
- `RequestUserAgentResolver` (`config/`): löst den UA für ausgehende HTTP-Calls auf — liest `X-Querchecker-User-Agent` aus dem aktuellen `RequestContextHolder`, Fallback: `UserAgentHolder.get()`. Wird von `WhApiClient` und `HtmlFetchService` verwendet. Konstante: `RequestUserAgentResolver.HEADER`.
- `QuickFactsResult` record: `(Map<String,String> quickFacts, List<FeatureGroup> featureGroups, Sources sources)`. `featureGroups` null beim Snippets-Pfad; befüllt beim HTML-Fetch-Pfad. `Sources(icecatId, sourceUrl)`.
- `LookupResponse`: `{ lookupStatus, quickFacts, icecatId, sourceType, sourceDomain, sourceUrl, featureGroupsJson }`.
- `GroqExtractionService` und `BraveSearchService`/`BraveResult` entfernt.
- `FieldSource` Enum (`research/entity/`): `SYSTEM` = benannte Felder für LLM-Pflichtfelder-Liste (nicht für Brave-Query geeignet); `USER` = Wert-Keywords für Brave-Query (nicht als quickFacts-Keys auswertbar — excluded from quality evaluation).

### CategorySearchSource Seeders (`research/seeder/`)

- `CategorySearchSourceDefinitions`: `SourceConfig` record `(domain, label, type, lookupEnabled, queryExcludes, searchResultCount)`. `CONFIGS` map mit 13 Kategorien: "Notebooks", "Smartphones / Handys", "Tablets", "Monitore", "Beamer", "Drucker", "PC-Komponenten", "Netzwerke", "Kameras / Camcorder", "Fernseher", "Konsolen", "Adapter / Kabel", "Software", "Spiele". Exakte DB-Namen (z.B. "/" nicht "&"; "Tablets" plural).
- `CategorySearchSourceSeeder`: additives Upsert beim Start. Verwendet `findAllByName` (nicht `findByName`) wegen doppelter Namen wie "Tablets" (Level-1 und Level-2). Setzt `inheritFromParent = (cat.getLevel() <= 2)` automatisch (Level-1 und Level-2 können als Fallback-Quellen für tiefere Kategorien dienen).
- `CategorySpecPreferenceSeeder` verwendet ebenfalls `findAllByName`.
- `WhRefreshScheduler`: `else`-Branch ruft nun nur Seeder auf (kein Willhaben-Fetch), wenn Kategorien bereits existieren. Beide Seeder (`categorySpecPreferenceSeeder.seedIfAbsent()` + `categorySearchSourceSeeder.seedIfAbsent()`) werden dort aufgerufen.

### CategorySearchSource (Flyway V30)

Tabelle `category_search_source` — konfiguriert Suchquellen pro Kategorie.

- `SourceType` enum: `ICECAT` | `FLATPANELSHD` | `GSMARENA` | `GENERIC`
- Felder: `id`, `whCategory` (nullable=default), `priority`, `siteDomain`, `siteLabel`, `sourceType`, `queryExcludes TEXT[]`, `searchResultCount` (default 10), `lookupEnabled` (default true), `inheritFromParent` (default false), `active` (default true)
- `queryExcludes`: `List<String>` mit `@Type(ListArrayType.class)` → PostgreSQL `TEXT[]` (`coreFields` wurde in V31 entfernt — Felder kommen jetzt aus `CategorySpecPreference`)
- Unique constraint: `(wh_category_id, site_domain)`
- `CategorySearchSourceRepository`:
  - `findByWhCategoryAndActiveTrueOrderByPriorityAsc(WhCategory)` — aktive Quellen
  - `findByWhCategoryAndInheritFromParentTrueAndActiveTrueOrderByPriorityAsc(WhCategory)` — Eltern-Fallback
  - `findByWhCategoryAndSiteDomain(WhCategory, String)` — für Seeder-Upsert
  - **Achtung**: `AndActiveTrueOrderByPriority` (nicht `AndActiveOrderByPriority`) — Spring Data braucht `True`-Suffix für implizite boolean=true Filterung ohne Parameter

---

## Build & Tooling

- Lombok + SpotBugs (Maven-Plugin, läuft bei `mvn verify`)
- `io.hypersistence:hypersistence-utils-hibernate-63:3.9.11` — `ListArrayType` für PostgreSQL `TEXT[]` Mapping (`CategorySearchSource.queryExcludes`)
- spring-boot-devtools: Hot-Restart bei Dateiänderungen
- CORS: allows `http://localhost:14072`
- DB: `jdbc:postgresql://localhost:14071/mydb`, user `myuser`
- **Spring Boot 3.5.3**: `jackson-bom.version=2.18.3` in pom.xml gepinnt (springdoc 2.8.6 inkompatibel mit Jackson 2.19.x)
- **`SpringDocConfig`** (`at.querchecker.config`): Überschreibt `PolymorphicModelConverter` mit ThreadLocal Cycle-Breaker um StackOverflow bei selbst-referenziellen DTOs (`WhCategoryDto.children`) zu verhindern
- Enum-Konvention: `@Enumerated(EnumType.STRING)` — kein nativer PostgreSQL-Enum-Typ (Flyway-Kompatibilität)

---

## API Usage (`api/`)

- `Provider` enum: `BRAVE`, `GROQ`, `OPENROUTER`, `ICECAT` (ehemals `GOOGLE` → umbenannt via Flyway `V29`)
- `RequestType` enum: `SEARCH`, `EXTRACTION`, `SPEC_DETAIL`, `HTML_FETCH`
- `ApiUsageLog`: neues Feld `model_name VARCHAR(100)` (Flyway V39) — welches LLM-Modell verwendet wurde
- `ApiUsageLogService.log()`: Signatur jetzt mit `String modelName` (null für Such-Provider)
  - Neue Methoden: `countRateLimitsByProviderAndPeriod()`, `sumEstimatedTokensForRateLimitsByProviderAndPeriod()`, `countByProviderAndModelNameAndPeriod()`, `sumTokensIn/OutByProviderAndModelNameAndPeriod()`
- `ApiUsageLogRepository`: neue Queries für 429-Count, geschätzte Tokens bei 429, per-Modell-Stats
- `QuotaService`: `checkQuota(Provider.ICECAT)` → immer `OK`; `isWarningThreshold(Provider.ICECAT)` → immer `false`
- `ApiUsageController` (`GET /api/usage`): übergibt jetzt `LocalDateTime now` an `providerUsage()` (verhindert Zeit-Drift); baut `groqModelBreakdown` via `buildGroqModelBreakdown()`
- `ProviderUsageDto`: `{ calls, tokensIn, tokensOut, quotaUsage, quotaLimit, model, quotaPeriod, rateLimitCount, rateLimitEstimatedTokens }`
- `UsageResponse`: `{ activeSearchProvider, activeLlmProvider, brave, googleDiscovery, groq, openRouter, groqModelBreakdown: List<ModelUsageDto> }`
- `ModelUsageDto` (neu): `{ model, calls, tokensIn, tokensOut }` — pro Modell-Subzeile in der Settings-Tabelle

## Token-Management für LLM-Requests

**Problem**: Groq hat 6000 TPM (Tokens Per Minute) limit. Vollständige Brave-Suchergebnisse können 6695 Tokens überschreiten → HTTP 413 "Payload Too Large"

**Lösung (2026-03-31, Commits 42d23ac + 343fb87)**: Lokale Snippet-Truncation ohne Token-Counter:

- **Warum kein Token-Counter**: Würde externe Library erfordern; TPM-Limits sind provider/modell-spezifisch; Simple Truncation ist 80/20-Lösung
- **Implementierung**: `BraveWebSearchService.extractResults()` mit Helper-Methoden `truncateString()`, `truncateSnippets()`
- **Parameter**: 5 Results × 250 char description + 7 snippets × 250 chars = ~3125 Tokens total (mit Prompt ~600 Tokens)
- **Headroom**: ~2875 Tokens frei (unter Limit mit Spielraum)
- **Google Discovery**: Unaffected (nur 1 snippet field, kein extraSnippets Array)

Die ersten 200-250 Zeichen eines Snippets enthalten typischerweise die wichtigste Info (Titel, initiale Kontext, Kernspezifikationen). Längere Snippets fügen wenig hinzu.

## Bug Fixes (Bekannte Fixes)

- `WhListingService.buildCategoryPath()`: fehlte `.id(current.getId())` im Builder → `activeCategoryId()` lieferte immer null → Stern-Klicks funktionierten nie. Behoben.

## Erweiterungsstrategie (Multi-Provider)

Bei weiteren Quellen (Geizhals etc.) → `BaseListing`-Superklasse mit `@Inheritance`, `WhListing` als Subklasse. `WhListingDetail` bleibt unverändert, FK zeigt dann auf `BaseListing`.
