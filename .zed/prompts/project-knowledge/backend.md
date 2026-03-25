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
│                   UserAgentHolder, UserAgentFilter
├── sse/            SseController, SseHub
├── api/
│   ├── entity/     ApiUsageLog, Provider (enum: BRAVE, GROQ, OPENROUTER, ICECAT), RequestType (enum)
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
│   │               LookupStatus (enum), FieldSource (enum: SYSTEM, USER),
│   │               CategorySearchSource, SourceType (enum: ICECAT, FLATPANELSHD, GSMARENA, GENERIC),
│   │               ExtractionQuality (enum: GOOD, PARTIAL, EMPTY, FAILED_NO_CRITERIA)
│   ├── model/      BraveApiResponse, QuickFactsResult (incl. featureGroups),
│   │               LookupRequest/Response, FullSpecsRequest/Response, ProductLookupResult,
│   │               SearchResult (generic: title, url, description, extraSnippets)
│   ├── repository/ CategorySpecPreferenceRepository, CategorySpecPreferenceFieldRepository,
│   │               ProductLookupRepository, CategorySearchSourceRepository
│   ├── config/     ResearchConfig
│   ├── seeder/     CategorySearchSourceDefinitions, CategorySearchSourceSeeder,
│   │               CategorySpecPreferenceSeeder, CategorySpecPreferenceDefinitions
│   └── services:   WebSearchService (interface), BraveWebSearchService, ProductLookupService,
│                   IcecatService, CategorySpecPreferenceService, CategorySearchSourceService,
│                   ExtractionQualityEvaluator, UrlValidator, HtmlFetchService
│       controllers: ProductLookupController
└── deepLearning/
    ├── entity/     DlModelConfig, DlExtractionRun, DlExtractionTerm, ItemText, WhItem
    ├── repository/ DlModelConfigRepository, DlExtractionRunRepository,
    │               DlExtractionTermRepository, ItemTextRepository, WhItemRepository
    ├── service/    DlOrchestrationService, DlExtractionService, DlPersistenceService,
    │               DlPromptResolver, DlCategoryPromptSeeder, DlFilterService,
    │               ExtractionModel (interface), ExtractionTask, GroqExtractionModel,
    │               AbstractExtractionModel, AbstractLlamaExtractionModel,
    │               Llama32ExtractionModel, MdebertaExtractionModel, ...
    ├── controller/ DlExtractionController
    └── DlCategoryPromptDefinitions (Konstanten für alle Prompts)
```

---

## Entities (alle mit Lombok: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)

### `WhListing` — Kerndata vom Willhaben-Inserat
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `whId` | String (unique, Willhaben-interne ID) |
| `title` | String |
| `description` | String |
| `price` | BigDecimal |
| `location` | String |
| `url` | String |
| `thumbnailUrl` | String (nullable) |
| `listedAt` | LocalDateTime |
| `fetchedAt` | LocalDateTime |

### `WhListingDetail` — User-Annotationen (1:1 zu WhListing, lazy-created)
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `whListing` | ManyToOne → WhListing |
| `note` | String (nullable) |
| `viewCount` | Integer (default 0) |
| `lastViewedAt` | LocalDateTime (nullable) |
| `rating` | Enum UP/DOWN/null |
| `createdAt` | LocalDateTime |
| `updatedAt` | LocalDateTime |

### `WhCategory` — Hierarchischer Kategoriebaum (3 Ebenen)
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `whId` | String (Willhaben ATTRIBUTE_TREE ID) |
| `name` | String |
| `level` | Integer (0=root, 1=sub, 2=sub-sub) |
| `parent` | ManyToOne → WhCategory (nullable) |

### `WhLocation` — Hierarchischer Standortbaum
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `areaId` | Integer (Willhaben areaId) |
| `name` | String |
| `level` | Integer (0=Bundesland, 1=Bezirk) |
| `parent` | ManyToOne → WhLocation (nullable) |

### `AppConfig` — Key-Value Konfiguration
| Feld | Typ |
|---|---|
| `key` | String (PK) |
| `value` | String |
| `description` | String |
| `updatedAt` | LocalDateTime |

---

## Deep-Learning-Extraction Entities (`deepLearning/entity/`)

### `ItemText` — Normalisierter Inseratstext (Eingabe für ML)
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `whListing` | ManyToOne → WhListing |
| `text` | String |

### `WhItem` — View-Entität über WhListing (Frontend-ID)
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `whListing` | ManyToOne → WhListing |

### `DlModelConfig` — ML-Modell-Konfiguration
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `name` | String |
| `active` | boolean |
| `executionOrder` | int (INT NOT NULL, kein DB-Default — muss in jeder Migration explizit gesetzt werden, Konvention: Vielfache von 10) |

### `DlExtractionRun` — Ausführungsprotokoll je Modell+Text
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `itemText` | ManyToOne → ItemText |
| `modelConfig` | ManyToOne → DlModelConfig |
| `status` | Enum (VARCHAR) |
| `durationMs` | Long (nullable, wall-clock ms) |
| `createdAt` | LocalDateTime |

### `DlExtractionTerm` — Erkannter Begriff je Run
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `run` | ManyToOne → DlExtractionRun |
| `term` | String |
| `confidence` | Double |

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

- `ExtractionClient` interface: `extractProductName(...)` + `extractQuickFacts(lookupTerm, categoryName, braveResults, mandatoryFields, prompt)` + `extractQuickFactsFromText(lookupTerm, categoryName, pageText, mandatoryFields, prompt)` (HTML-Fetch-Pfad — `{snippets}` wird mit Jsoup-Output befüllt)
- `AbstractLlmExtractionClient`: `callLlm()`, JSON-Parsing, `applyIcecatIdSafetyCheck()` (case-insensitive URL-Match), `formatSnippets()`. Parst `featureGroups` aus JSON wenn vorhanden.
- `GroqExtractionClient` / `OpenRouterExtractionClient`: OpenAI-kompatibles API
- `ExtractionProviderRouter`: aktiver Provider via `querchecker.api.extraction.active-provider` (GROQ | OPENROUTER)

## DL Category Prompts

- `DlCategoryPromptDefinitions`: Java-Konstanten für alle Prompts. Enthält `PromptConfig` record `(PromptType, systemPrompt, userPrompt)`. Default-Konstanten: `PRODUCT_NAME_SYSTEM`, `PRODUCT_NAME_USER_DEFAULT`, `QUICK_FACTS_SYSTEM`, `QUICK_FACTS_USER_DEFAULT`. Kategorie-spezifische Prompts in unified `CONFIGS: Map<String, List<PromptConfig>>` (ersetzt die früheren getrennten `*_BY_CATEGORY`-Maps).
- `DlCategoryPromptSeeder`: Additives Per-Entry-Upsert beim Start — prüft jedes `(Kategorie, PromptType)`-Paar einzeln via `findDefaultByPromptType` + `findByWhCategoryAndPromptType`. Überschreibt niemals vorhandene Einträge. Re-seed: `DELETE FROM dl_category_prompt` + Neustart.
- `DlPromptResolver.resolve(WhCategory, PromptType)`: traversiert Kategoriehierarchie, Fallback auf Default
- **QUICK_FACTS icecatId**: "die rein numerische ID am Ende der icecat-URL, direkt vor .html"

## GroqExtractionModel

- Implementiert `ExtractionModel`, delegiert an `ExtractionProviderRouter.getActive().extractProductName()`
- Length-Guard: Terme > 150 Zeichen werden verworfen (verhindert generische Halluzinationen)
- DB: `model_name='groq'`, `source='API'`, `execution_order=5`
- `ModelSource` Enum: `HUGGINGFACE`, `LOCAL`, `API`

## Research-Package (`research/`)

- `WebSearchService` (Interface): `search(lookupTerm, siteDomain, keywords, queryExcludes, resultCount)` → `List<SearchResult>`. Aktivierung per `querchecker.api.search.active-provider` (default: `BRAVE`).
- `BraveWebSearchService implements WebSearchService`: 3-stufige Suche. Kein `Accept-Encoding: gzip` Header. Ersetzt `BraveSearchService`.
- `SearchResult` record: `(title, url, description, extraSnippets)` — generisch. Ersetzt `BraveResult`.
- `ProductLookupService`: Multi-Source-Schleife über `CategorySearchSource`-Liste. Je Quelle: Brave → HTML-Fetch (FLATPANELSHD/GSMARENA via Jsoup-Fallback-Loop) oder Snippets (ICECAT/GENERIC) → LLM → Quality-Check → weiter bei PARTIAL/EMPTY. Speichert `sourceType`, `sourceDomain`, `sourceUrl`, `featureGroupsJson` in `ProductLookup`.
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
  - `matchesExpectedPattern(url, SourceType)` — Regex: ICECAT `icecat.biz/p/[name]-[id].html`, GSMARENA `gsmarena.com/[name]-[id].php`, FLATPANELSHD `flatpanelshd.com/[name].php`; GENERIC passt immer
- `HtmlFetchService`: `shouldFetchFullPage(SourceType)` (true für FLATPANELSHD/GSMARENA). `fetchAndExtract(url, SourceType)`: Jsoup-Fetch mit 10s Timeout, site-spezifische CSS-Selektoren (GSMArena: `table.specs-phone-big-table`; FlatpanelsHD: `table.specsTable, div.specs, table.tv-specs` mit `main`-Fallback).
- `UserAgentHolder`: erfasst ersten Browser-User-Agent aus eingehenden Requests; Fallback: hardcodierter Chrome-UA. `UserAgentFilter`: Jakarta Servlet Filter; speichert UA bei jedem Request in `UserAgentHolder`.
- `QuickFactsResult` record: `(Map<String,String> quickFacts, List<FeatureGroup> featureGroups, Sources sources)`. `featureGroups` null beim Snippets-Pfad; befüllt beim HTML-Fetch-Pfad. `Sources(icecatId, sourceUrl)`.
- `LookupResponse`: `{ lookupStatus, quickFacts, icecatId, sourceType, sourceDomain, sourceUrl, featureGroupsJson }`.
- `GroqExtractionService` und `BraveSearchService`/`BraveResult` entfernt.
- `FieldSource` Enum (`research/entity/`): `SYSTEM` = benannte Felder für LLM-Pflichtfelder-Liste (nicht für Brave-Query geeignet); `USER` = Wert-Keywords für Brave-Query (nicht als quickFacts-Keys auswertbar — excluded from quality evaluation).

### CategorySearchSource Seeders (`research/seeder/`)
- `CategorySearchSourceDefinitions`: `SourceConfig` record `(domain, label, type, lookupEnabled, queryExcludes, searchResultCount)`. `CONFIGS` map mit 13 Kategorien: "Notebooks", "Smartphones / Handys", "Tablets", "Monitore", "Beamer", "Drucker", "PC-Komponenten", "Netzwerke", "Kameras / Camcorder", "Fernseher", "Konsolen", "Adapter / Kabel", "Software", "Spiele". Exakte DB-Namen (z.B. "/" nicht "&"; "Tablets" plural).
- `CategorySearchSourceSeeder`: additives Upsert beim Start. Verwendet `findAllByName` (nicht `findByName`) wegen doppelter Namen wie "Tablets" (Level-1 und Level-2). Setzt `inheritFromParent = (cat.getLevel() == 1)` automatisch.
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
- `RequestType` enum: `SEARCH`, `EXTRACTION`, `SPEC_DETAIL`, `HTML_FETCH` (`HTML_FETCH` neu für FlatpanelsHD/GSMArena HTML-Fetch-Logging)
- `ApiUsageLogService`: `log()`, `countByProviderAndPeriod()`, `sumTokensInputByProviderAndPeriod()`, `sumTokensOutputByProviderAndPeriod()`. `avgDurationByProvider()` entfernt.
- `ApiUsageLogRepository`: `sumTokensInputByProviderAndCreatedAtBetween` + `sumTokensOutputByProviderAndCreatedAtBetween` Queries.
- `QuotaService`: `checkQuota(Provider.ICECAT)` → immer `OK`; `isWarningThreshold(Provider.ICECAT)` → immer `false` (kein Kontingent).
- `ApiUsageController` (`GET /api/usage`): Periode/Limits kommen aus `QuotaService.getPeriodStart()` + `ProviderProperties` (nicht hardcodiert).
- `ProviderUsageDto`: `{ callsThisPeriod, callsToday, tokensIn, tokensOut, quotaUsage, quotaLimit }`. Kein `avgDurationMs`.
- `UsageResponse`: `{ brave, groq, openRouter }` — ICECAT entfernt (kein Kontingent, kein Monitor-Eintrag).

## Bug Fixes (Bekannte Fixes)

- `WhListingService.buildCategoryPath()`: fehlte `.id(current.getId())` im Builder → `activeCategoryId()` lieferte immer null → Stern-Klicks funktionierten nie. Behoben.

## Erweiterungsstrategie (Multi-Provider)

Bei weiteren Quellen (Geizhals etc.) → `BaseListing`-Superklasse mit `@Inheritance`, `WhListing` als Subklasse. `WhListingDetail` bleibt unverändert, FK zeigt dann auf `BaseListing`.
