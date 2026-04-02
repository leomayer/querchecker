# Architecture & Design Decisions

Technical deep-dive for developers interested in how Querchecker is built.

---

## Frontend Architecture

### @ngrx/signals SignalStore

Instead of traditional RxJS Subject-based state management, Querchecker uses **@ngrx/signals** for fine-grained reactivity:

- **SearchStore** (`features/wh-search/search.store.ts`): Global app state (listings, filters, layout state)
- **ExtractionStore** (`features/wh-search/extraction.store.ts`): DL extraction results, spec-lookup cache, Icecat data
- **Computed signals** auto-track dependencies — no manual subscription cleanup needed
- **Effects** for side effects (HTTP calls, navigation)

**Benefit**: Less boilerplate than traditional RxJS, better compiler optimizations, easier reasoning about data flow.

### Angular 21+ Modern Syntax

- **Standalone Components** — no NgModules, tree-shake unused code
- **Control Flow** — `@if`, `@for`, `@switch` instead of `*ngIf`, `*ngFor`
- **Input/Output** — `input()` / `output()` function APIs instead of decorators
- **httpResource** — reactive HTTP state management (loading, data, error)

### Component Hierarchy

```
MainLayoutComponent (route container, two httpResources: allListings + search)
├── SearchStore (global state)
├── ExtractionStore (global spec-lookup cache)
├── zone-left (filter panel)
│   ├── location-filter
│   ├── category-filter
│   ├── wh-filter
│   └── wh-sort
└── zone-right (results / detail)
    ├── app-wh-listings (LISTINGS state)
    │   └── listing-card × N
    ├── app-wh-detail (DETAIL state)
    │   ├── wh-base (gallery, price, meta)
    │   ├── item-annotation (rating, interest, notes)
    │   └── item-research (spec-lookup, DL terms, Icecat accordion)
```

**State Machine**: SEARCH → LISTINGS → DETAIL with smooth animations.

---

## Backend Architecture

### Spring Boot 3.5 + Java 21

- **Lombok** (`@Data`, `@Builder`, `@NoArgsConstructor`) for boilerplate reduction
- **SpotBugs** (Maven plugin) catches common bugs at compile time
- **JPA/Hibernate** with PostgreSQL 16
- **Jackson 2.18.3** (pinned in pom.xml — springdoc 2.8.6 incompatible with 2.19.x)
- **spring-boot-devtools** for hot-reload on file save

### Package Structure

```
at.querchecker/
├── entity/          WhListing, WhListingDetail, WhCategory, WhLocation, AppConfig
├── dto/             Generated + manual DTOs
├── controller/      REST endpoints
├── service/         Business logic
├── repository/      JPA repositories
├── config/          Beans, interceptors, UA forwarding
├── sse/             Server-Sent Events (SSE) for async operations
├── api/             LLM extraction & API usage logging
├── wh/              Willhaben integration
├── research/        Spec-lookup, Brave search, quality evaluation
└── deepLearning/    DL orchestration, model management, prompts
```

### Conditional Model Registration

**Problem**: Initializing local GGUF files at startup wastes memory if models aren't active.

**Solution**: `DlModelConfiguration` with `@EventListener(ApplicationReadyEvent.class)` registers models **after** full context initialization:

- **API mode** (`querchecker.llm.mode=API`): Only Groq cloud model
- **LOCAL mode**: Query DB for active `DlModelConfig`, register only those

```java
@EventListener(ApplicationReadyEvent.class)
public void registerModels() {
    List<DlModelConfig> active = modelConfigRepo.findByActiveTrueOrderByExecutionOrderAsc();
    active.forEach(cfg -> {
        ExtractionModel model = createModel(cfg);
        applicationContext.getBeansOfType(ExtractionModel.class).put(cfg.getModelName(), model);
    });
}
```

**Benefit**: No GGUF files loaded unless needed. Scalable to many models.

### Sequential DL Execution

**Queue Architecture** (`DlOrchestrationService`):

- `LinkedBlockingDeque<Runnable>` (unbounded) + `ThreadPoolExecutor(1,1)` — globally sequential, never parallel
- **Priority by `executionOrder`**: Models sorted DESC, `addFirst()` → lowest executionOrder runs first
- **Queue limit** (default 10): Overflow → `pollLast()` → lowest-priority run marked `CANCELLED`
- **Duplicate check**: `existsByItemTextAndModelConfigAndStatusIn([DONE, INIT, PENDING])` — CANCELLED not skipped, enables retries

Why sequential? Prevents thundering herd on Groq API, consistent results.

### SSE for Async Operations

**Server-Sent Events** stream results as they complete:

```
User opens detail → scheduleExtraction() → DL queued (INIT)
  → Model runs → DlPersistenceService.saveResults()
    → publishEvent(DlExtractionCompletedEvent)
      → DlExtractionController resolves itemTextId → whItemId
        → SseHub.broadcast("dl-extract", { whItemId, terms, suggestedTerm })
```

Frontend receives results **per model** (Groq first, then Llama), not after all complete.

### Multi-Source Lookup with Fallback

**ProductLookupService** loop (per `CategorySearchSource`):

```
For each source (ordered by priority):
  1. Brave Search → SearchResult[]
  2. HTML-Fetch (FLATPANELSHD/GSMARENA) OR Snippets (ICECAT/GENERIC) → pageText
  3. LLM extraction (extractQuickFacts or extractQuickFactsFromText) → QuickFactsResult
  4. Quality evaluation (GOOD/PARTIAL/EMPTY)
     → GOOD: stop, persist COMPLETE
     → PARTIAL/EMPTY: try next source
  5. Exception (rate-limit, network): retry async if ≤20s, else FAILED
```

**Caching semantics**:
- `COMPLETE` — permanent
- `FAILED` — TTL 24h
- `ERROR` — TTL 10min
- `RATE_LIMITED` — not persisted, async retry
- `NO_SOURCES` — virtual, re-checked each call

---

## API Integration

### Extraction Client Interface

```java
interface ExtractionClient {
    extractProductName(title, description, categoryName, prompt)
    extractQuickFacts(lookupTerm, categoryName, braveResults, mandatoryFields, prompt)
    extractQuickFactsFromText(lookupTerm, categoryName, pageText, mandatoryFields, prompt)
}
```

Implementations: `GroqExtractionClient`, `OpenRouterExtractionClient` (both OpenAI-compatible).

**Router** (`ExtractionProviderRouter`): Active provider via `querchecker.api.extraction.active-provider` config.

### LLM Extraction Robustness

**AbstractLlmExtractionClient** applies:

1. **Sanitization** — fix inch-mark errors (e.g., `"24""` → `"24 Zoll"`)
2. **Filler-value stripping** — remove "unbekannt", "-", "n/a", etc. from quickFacts
3. **JSON parsing with fallback** — if parse fails, try `tryParseJson()` with error recovery
4. **icecatId validation** — case-insensitive pattern matching against Brave results

---

## Database Design

### Entities with Lombok

All entities use `@Data @Builder @NoArgsConstructor @AllArgsConstructor` for minimal boilerplate.

### Key Foreign Keys

- `WhListingDetail.whListing` → 1:1 (User annotations)
- `DlExtractionRun.itemText` + `modelConfig` → 2D index (per-model results)
- `DlExtractionTerm.run` → many terms per run
- `ProductLookup.whCategory` → nullable (default sources)

### Arrays in PostgreSQL

**`CategorySearchSource.queryExcludes`**: `TEXT[]` mapped via `@Type(ListArrayType.class)` from `hypersistence-utils-hibernate-63:3.9.11`.

---

## DevTools & Hot-Reload

**`src/main/resources/META-INF/spring-devtools.properties`**:

```properties
restart.exclude=llama-.*\.jar
```

Why? Llama JNI native library (`libjllama.so`) can't be reloaded by RestartClassLoader. Without this, hot-restarts fail with `UnsatisfiedLinkError`.

---

## OpenAPI Code Generation

**Workflow**:

```bash
cd frontend
npm run generate-api  # Calls openapi-generator-cli
```

Reads Swagger spec from backend (`/v3/api-docs`), generates:
- DTOs (`api/model/*.ts`)
- Service classes (`api/api/*.service.ts`)

**Important**: Generated service classes are NOT used. Only DTO types imported. Hand-written services in `core/` handle actual HTTP calls (more control, less magic).

---

## Configuration

### application.yml Structure

```yaml
querchecker:
  dl:
    min-confidence: 0.0
    top-k: 5
    source-model: llama  # Only this model sends suggestedTerm in SSE

  llm:
    mode: API  # or LOCAL

  api:
    extraction:
      active-provider: GROQ  # or OPENROUTER
    providers:
      brave:
        free-limit: 1000
        free-limit-period: MONTHLY
      groq:
        model: llama-3.1-8b-instant
        free-limit: 25000
        free-limit-period: DAILY
```

API keys in `secret.yml` (not in Git).

---

## Testing Strategy

- **Unit Tests** — Mockito for services, repositories
- **Integration Tests** — Real DB (testcontainers), actual HTTP calls where feasible
- **Frontend** — Vitest (Angular 21+ default) with Jasmine syntax

Current coverage: 122 tests passing.

---

## Performance Optimizations

1. **Snippet Truncation** — Brave results capped to 5 results, 250 chars per description, 7 snippets × 250 chars max (~3125 tokens total, well under 6000 TPM limit)
2. **Lazy Model Initialization** — Local GGUF files only loaded if active
3. **Search Result Caching** — `SearchResultCacheService` in-memory cache (for retry on rate-limit)
4. **Listing Detail Projection** — `WhListingDetailSummary` interface for efficient joins (viewCount, rating without loading full entity)

---

## Deployment & Ops

See [docs/robustness.md](robustness.md) for error handling and monitoring.

Production deployment: Docker containers orchestrated via `docker-compose.prod.yml`, Traefik reverse proxy with SSL via Let's Encrypt.
