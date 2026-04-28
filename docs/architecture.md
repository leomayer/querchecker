# Architecture & Design Decisions

Technical deep-dive for developers interested in how Querchecker is built.

---

## Frontend Architecture

### @ngrx/signals SignalStore

Instead of traditional RxJS Subject-based state management, Querchecker uses **@ngrx/signals** for fine-grained reactivity:

- **SearchStore** (`features/wh-search/search.store.ts`): Global app state — listings, filters, layout state
- **ExtractionStore** (`features/wh-search/extraction.store.ts`): DL extraction results, item research cache, Icecat data

Computed signals auto-track their dependencies, no manual subscription cleanup needed. Effects handle side effects (HTTP calls, navigation). The result is less boilerplate than traditional RxJS and easier reasoning about data flow.

### Angular 21+ Modern Syntax

The codebase uses only current Angular patterns throughout:

- **Standalone Components** — no NgModules
- **Control Flow** — `@if`, `@for`, `@switch` instead of structural directives
- **`input()` / `output()`** — function APIs instead of decorators
- **`httpResource()`** — reactive HTTP state (loading, data, error) without manual subscriptions
- **No `ChangeDetectionStrategy.OnPush`** — Signals handle granularity, the decorator adds no value here

### State Machine & Navigation

The app uses a three-state layout machine (`SEARCH → LISTINGS → DETAIL`) managed in `SearchStore`. Each transition updates the route, so browser back/forward navigation works as expected. Transitions additionally trigger CSS animations for a smooth handoff between states. Filter state, scroll position, and loaded listings are preserved in the store, so navigating back from a detail view restores the previous results instantly without a new API call.

The Settings page (`/settings`) is a separate route outside the state machine.

### Component Hierarchy

```
MainLayoutComponent
├── SearchStore (global state)
├── ExtractionStore (item research + DL cache)
├── zone-left (filter panel)
│   ├── location-filter
│   ├── category-filter
│   └── wh-filter / wh-sort
└── zone-right
    ├── wh-listings (LISTINGS state)
    │   └── listing-card × N
    └── wh-detail (DETAIL state)
        ├── wh-base (gallery, price, meta)
        ├── item-annotation (rating, notes)
        └── item-research (DL terms, item research, Icecat accordion)
```

---

## Backend Architecture

### Package Structure

```
at.querchecker/
├── entity/       Core domain (WhListing, WhListingDetail, WhCategory, …)
├── controller/   REST endpoints
├── service/      Business logic
├── repository/   JPA repositories
├── config/       Beans, interceptors, UA forwarding
├── sse/          Server-Sent Events hub
├── api/          LLM extraction clients, usage logging
├── wh/           Willhaben integration
├── research/     Item research, web search, quality evaluation
└── deepLearning/ DL orchestration, model management, prompts
```

### Conditional Model Registration

**Problem**: Local GGUF model files are large and cannot be unloaded at runtime. Initializing them unconditionally wastes memory whenever the app runs in API mode.

**Solution**: `DlModelConfiguration` listens for `ApplicationReadyEvent` and registers models **after** the full context is initialized (database available):

- **API mode** (`querchecker.llm.mode=API`): Only `LlmApiExtractionModel` is registered
- **LOCAL mode**: Active entries from `DlModelConfig` table are queried; only those are instantiated as singletons

`DlOrchestrationService` uses `ObjectProvider<List<ExtractionModel>>` for lazy dependency resolution — avoids circular initialization and handles the case where no models are registered cleanly.

**Benefit**: No GGUF file is loaded unless it's both active in the DB and LOCAL mode is configured. Adding a new model type requires only a DB row and a new `ExtractionModel` implementation.

Note: Local GGUF inference is impractically slow without a GPU. LOCAL mode is documented as a community option; the intended production setup is API mode (Groq or OpenRouter).

### Sequential DL Execution

**Queue architecture** (`DlOrchestrationService`):

- `LinkedBlockingDeque<Runnable>` + `ThreadPoolExecutor(1,1)` — globally sequential, never parallel
- Models are sorted by `executionOrder`; `addFirst()` gives fast models priority over slow ones
- Queue overflow (default: 10): `pollLast()` removes the lowest-priority pending run and marks it `CANCELLED`
- `CANCELLED` is not terminal — duplicate detection skips `CANCELLED` status, so re-opening a listing automatically retries

Why sequential? Prevents thundering-herd on Groq's free tier rate limits and produces consistent, comparable results across models.

### SSE for Async Operations

Results stream to the frontend as they complete rather than waiting for all models to finish:

```
User opens detail → scheduleExtraction()
  → DlOrchestrationService queues run (status: INIT)
    → model executes → DlPersistenceService.saveResults()
      → ApplicationEventPublisher fires DlExtractionCompletedEvent
        → DlExtractionController resolves itemTextId → whItemId
          → SseHub.broadcast("dl-extract", { whItemId, terms, suggestedTerm })
```

The frontend receives partial results per model in real time. No polling, no loading spinner waiting for the slowest model.

### Provider-Agnostic Design

Neither the web search layer nor the LLM extraction layer is tied to a specific vendor.

**Web search**: `WebSearchService` interface with `search(lookupTerm, siteDomain, keywords, queryExcludes, resultCount)`. Active provider is selected via `querchecker.api.search.active-provider`. Current implementations: `BraveWebSearchService`, `GoogleDiscoveryWebSearchService`.

**LLM extraction**: `ExtractionClient` interface with implementations for Groq and OpenRouter (both OpenAI-compatible). `ExtractionProviderRouter` selects the active one via `querchecker.api.extraction.active-provider`. Switching providers requires only a config change, no code change.

This pattern also makes quota handling straightforward: `ApiUsageLogService` tracks calls and tokens per `Provider` enum entry regardless of which implementation is active.

### Item Research Flow

Fetching specs for a listing is a multi-step pipeline that can take several seconds. Each step is a potential failure point with its own fallback:

```
lookupTerm
  → Cache check (ProductLookup by lookupTerm)
      → COMPLETE: serve cached result, done
      → FAILED / ERROR: respect TTL — skip pipeline while active
  → Quota check
      → QUOTA_EXCEEDED: reject, done
  → Multi-source loop (CategorySearchSource per category, priority ASC)
      → ICECAT / GENERIC: Web Search (Brave or Google Discovery) → snippets → LLM
      → GSMARENA / FLATPANELSHD: HTML fetch (Jsoup) → full page text → LLM
        → Quality Evaluation (GOOD / PARTIAL / EMPTY)
            → GOOD: persist COMPLETE, done
            → PARTIAL / EMPTY: try next source
              → ...
      → All sources exhausted: persist FAILED
```

Each category has an ordered list of `CategorySearchSource` entries (e.g. ICECAT → GSMARENA → GENERIC). The loop continues until a `GOOD` result is found or all sources are exhausted. Results are cached permanently on `COMPLETE` — subsequent lookups for the same `lookupTerm` skip the pipeline entirely. Because `lookupTerm` is the cache key (not the listing ID), multiple listings for the same product share one cached result automatically.

**Web search providers** differ in a fundamental way:

- **Brave Search**: General web index. Any URL can appear in results — product pages, spec sheets, review sites. Supports dynamic discovery of new or obscure products. Free tier: 1,000 requests/month.
- **Google Discovery Engine**: Requires a pre-configured data store of indexed URLs. Results are fast and high-quality for known sources, but products not in the index won't be found. Suited for production deployments with a curated source corpus. Pricing and quota differ significantly from Brave.

The active provider is switched via `querchecker.api.search.active-provider` with no code changes required. Both implement `WebSearchService`.

> For implementation-level differences between providers (snippet format, locale deduplication, pageSize workaround), see 🤖 [KI-Produktanalyse — Suchquellen](ki-produktanalyse.md#suchquellen-multi-source-loop).

### LLM Robustness

`AbstractLlmExtractionClient` applies several hardening layers before returning results:

1. **Sanitization** — fixes inch-mark encoding errors in raw LLM output (e.g. `24""` → `24 Zoll`) before JSON parsing
2. **Filler-value stripping** — removes "unbekannt", "-", "n/a", "unknown" etc. from extracted fields so they don't count as fulfilled in quality evaluation
3. **JSON retry** — if the first parse fails, a second attempt with error recovery is made before giving up
4. **icecatId validation** — extracted IDs are cross-checked against the actual Brave Search result URLs to prevent hallucinations
5. **Rate-limit tracking** — on 429 responses, estimated input token counts are logged so rate-limit hits appear in the usage monitor

---

## Database Design

### Enum Strategy

All enums use `@Enumerated(EnumType.STRING)` stored as `VARCHAR` rather than native PostgreSQL enum types. Native PG enums require DDL changes (`ALTER TYPE`) to add values, which conflicts with Flyway's sequential migration model. String columns can be extended with a simple `ALTER TABLE`.

### PostgreSQL Arrays

`CategorySearchSource.queryExcludes` stores a `TEXT[]` array, mapped via `@Type(ListArrayType.class)` from `hypersistence-utils`. This allows per-source keyword exclusions without a join table for what is essentially a simple list.

---

## OpenAPI Code Generation

```bash
cd frontend && npm run generate-api  # reads from backend /v3/api-docs
```

Generated output lands in `src/app/api/` and is committed to Git. Generated **service classes are not used** — only the DTO types are imported. All HTTP calls go through hand-written services in `core/`, which gives full control over request construction, error handling, and SSE event wiring without fighting generated abstractions.

---

## Deployment & Ops

Production deployment uses Docker containers via `docker-compose.prod.yml` with a Traefik reverse proxy handling SSL via Let's Encrypt.

See 🛡️ [Robustness & Error Handling](robustness.md) for error handling, quota management, and startup behaviour.
