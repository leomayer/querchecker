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
├── config/         CorsConfig, RestTemplateConfig, SpringDocConfig
├── sse/            SseController, SseHub
├── api/
│   ├── entity/     ApiUsageLog, Provider (enum), RequestType (enum)
│   ├── extraction/ ExtractionClient (interface), AbstractLlmExtractionClient,
│   │               GroqExtractionClient, OpenRouterExtractionClient,
│   │               ExtractionProviderRouter
│   ├── model/      ChatRequest, ChatResponse
│   └── service/    ApiUsageLogService, QuotaService
├── wh/             WhSearchController, WhSearchService, WhMetaController,
│                   WhCategoryService, WhLocationService, WhRefreshScheduler,
│                   api/WhApiResponse.java
├── research/
│   ├── entity/     CategorySpecPreference, ProductLookup, LookupStatus (enum)
│   ├── model/      BraveResult, BraveApiResponse, QuickFactsResult,
│   │               LookupRequest/Response, FullSpecsRequest/Response, ProductLookupResult
│   ├── repository/ CategorySpecPreferenceRepository, ProductLookupRepository
│   ├── config/     ResearchConfig
│   └── services:   BraveSearchService, GroqExtractionService, ProductLookupService,
│                   IcecatService, IcecatClient, EanSearchClient,
│                   CategorySpecPreferenceService
│       controllers: ProductLookupController, ResearchController
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
- `WhCategoryRepository`, `WhLocationRepository`, `AppConfigRepository` – Standard JpaRepository
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

### Research (`ResearchController`)
- `GET /api/research/product/search` — EAN/UPC-Suche
- `GET /api/research/icecat/{ean}` — Icecat-Specs per EAN

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

- `ExtractionClient` interface: `extractProductName(title, description, categoryName, DlCategoryPrompt)` + `extractQuickFacts(...)`
- `AbstractLlmExtractionClient`: `callLlm()`, JSON-Parsing, `applyIcecatIdSafetyCheck()` (case-insensitive URL-Match), `formatSnippets()`
- `GroqExtractionClient` / `OpenRouterExtractionClient`: OpenAI-kompatibles API
- `ExtractionProviderRouter`: aktiver Provider via `querchecker.api.extraction.active-provider` (GROQ | OPENROUTER)

## DL Category Prompts

- `DlCategoryPromptDefinitions`: Java-Konstanten für alle Prompts (PRODUCT_NAME + QUICK_FACTS, default + kategorie-spezifisch)
- `DlCategoryPromptSeeder`: seeded DB idempotent bei Start (INSERT wenn count=0 je prompt_type)
- Nach Prompt-Änderungen: DB-Zeilen löschen + Neustart. Flyway V27/V28 löschen PRODUCT_NAME/QUICK_FACTS.
- `DlPromptResolver.resolve(WhCategory, PromptType)`: traversiert Kategoriehierarchie, Fallback auf Default
- **QUICK_FACTS icecatId**: "die rein numerische ID am Ende der icecat-URL, direkt vor .html"

## GroqExtractionModel

- Implementiert `ExtractionModel`, delegiert an `ExtractionProviderRouter.getActive().extractProductName()`
- Length-Guard: Terme > 150 Zeichen werden verworfen (verhindert generische Halluzinationen)
- DB: `model_name='groq'`, `source='API'`, `execution_order=5`
- `ModelSource` Enum: `HUGGINGFACE`, `LOCAL`, `API`

## Research-Package (`research/`)

- `BraveSearchService`: 3-stufige Suche (exakt, icecat.biz-scoped, breiter). Kein `Accept-Encoding: gzip` Header (RestTemplate kann nicht dekomprimieren)
- `GroqExtractionService.extractFromSnippets(lookupTerm, whCategory, braveResults, mandatoryFields)`: löst Prompt per `DlPromptResolver` auf, ruft aktiven LLM-Provider auf → `QuickFactsResult { quickFacts, sources.icecatId, sources.icecatUrl }`
- `ProductLookupService`: orchestriert Lookup — BraveSearch → GroqExtractionService → speichert in `ProductLookup`
- `IcecatService`: Icecat-API nach `icecatId` (numerische ID), gibt `icecatSpecsJson` zurück
- `CategorySpecPreferenceService`: verwaltet Pflichtfelder je Kategorie

---

## Build & Tooling

- Lombok + SpotBugs (Maven-Plugin, läuft bei `mvn verify`)
- spring-boot-devtools: Hot-Restart bei Dateiänderungen
- CORS: allows `http://localhost:14072`
- DB: `jdbc:postgresql://localhost:14071/mydb`, user `myuser`
- **Spring Boot 3.5.3**: `jackson-bom.version=2.18.3` in pom.xml gepinnt (springdoc 2.8.6 inkompatibel mit Jackson 2.19.x)
- **`SpringDocConfig`** (`at.querchecker.config`): Überschreibt `PolymorphicModelConverter` mit ThreadLocal Cycle-Breaker um StackOverflow bei selbst-referenziellen DTOs (`WhCategoryDto.children`) zu verhindern
- Enum-Konvention: `@Enumerated(EnumType.STRING)` — kein nativer PostgreSQL-Enum-Typ (Flyway-Kompatibilität)

---

## Erweiterungsstrategie (Multi-Provider)

Bei weiteren Quellen (Geizhals etc.) → `BaseListing`-Superklasse mit `@Inheritance`, `WhListing` als Subklasse. `WhListingDetail` bleibt unverändert, FK zeigt dann auf `BaseListing`.
