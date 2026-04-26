# Open Issues — Querchecker v0.2.0

> Consolidated from: todo-quecker-1.md, openrouter-completion.md, provider-config.md, code analysis.
> Priority: H = blocking / M = important / L = polish/nice-to-have

---

## Features (Product)

| # | Title | Priority | Notes |
|---|-------|----------|-------|
| F1 | Similar listings | M | Clientside only — Substring match in SearchStore `patchedListings` by extracted model name. Active listing excluded. No API call. Details: `todo-quecker-1.md` |
| F2 | Mobile layout | L | No design spec yet |
| F3 | More platforms (eBay Kleinanzeigen, Shpock) | L | Ideation only |
| F4 | Market price comparison (multi-shop) | L | Brave per-shop queries; high quota cost. Geizhals deep-link is current workaround |

---

## Technical Debt / Refactoring

| # | Title | Priority | Notes |
|---|-------|----------|-------|
| T1 | Category lookup config → Settings UI | M | `category_search_source` currently seeded from Java `CategorySearchSourceDefinitions` — additive only, change requires DB DELETE + restart. Goal: CRUD in Settings/Provider. REST: GET/POST/DELETE for `CategorySearchSource`. Details: `todo-quecker-1.md` |
| T2 | Category prompt config → Settings UI | M | Same problem as T1 for `dl_category_prompt` / `DlCategoryPromptDefinitions`. Details: `todo-quecker-1.md` |
| T3 | Provider switching at runtime (no restart) | M | `WebSearchProviderRouter` reads from static `SearchProperties` (application.yml). Goal: read active provider from `AppConfig` (DB) so it's switchable at runtime. Block F step 15 in `provider-config.md` |
| T4 | Angular component naming convention | L | Drop `.component` suffix per Angular 20+ convention. Details: `memory/refactor_component_naming.md` |
| T5 | Remove unused `ConfigController` | M | `GET /api/config/providers` is superseded by `GET /api/provider-status`. Generated `ConfigService` has no active consumers. `item-research.aiSearchEnabled` still `input(true)` stub — needs wiring to `ProviderStatusStore`. Block D in `provider-config.md` |

---

## Provider Setup Feature (provider-config.md blocks)

The setup wizard UI exists but several blocks are partially or not yet implemented.
Full spec in `docs/concepts/provider-config.md`.

| # | Block | Status | What's needed |
|---|-------|--------|---------------|
| P1 | Block B — ProviderStatusStore + Popup + Badge | Partial | `ProviderStatusStore` (read-only, SSE-driven). Hash-based popup on UNCONFIGURED. Badge on Settings button with Warning/Error color. |
| P2 | Block B1 — UI wording | Open | (a) DL-Extraction → user-friendly German term. (b) "Spec-Lookup" → clearer description. (c) Validation table layout (Icon \| Provider \| Selected \| Configured \| Validated). (d) LLM-missing warning more prominent in popup. (e) `--color-tertiary` contrast fix for light mode. (f) Badge padding too small + invisible in dark mode |
| P3 | Block E — Test-Button + Lazy Validation feedback | Open | Test-Button in Settings (remote providers only, CONFIGURED/UNREACHABLE/UNAVAILABLE). Lazy validation: first call failure → SSE event → frontend state update |
| P4 | Block F step 15 — Runtime provider switch | Open | See T3 above |

---

## OpenRouter Completion

> Full implementation guide: `docs/openrouter-completion.md` — paste-ready code, no analysis needed.

| # | Gap | Priority | Effort |
|---|-----|----------|--------|
| OR1 | `openRouterRestClient()` missing startup API key log | L | 1 line |
| OR2 | `OpenRouterExtractionClient.getApiKeyInfo()` not overridden | L | ~5 lines |
| OR3 | `response_format: json_object` sent to all providers — some OpenRouter models return HTTP 400 | M | Add `supportsJsonResponseFormat()` hook; OpenRouter overrides to `false` |
| OR4 | No `free-limit` for OpenRouter in YAML — no quota bar in Usage Monitor | L | Config-only if budget tracking is wanted |

---

## Testing Gaps

### Backend

| # | Area | Missing |
|---|------|---------|
| BT1 | `WhListingService` / `WhSearchService` | No test files visible |
| BT2 | `DlExtractionController` (SSE broadcasting) | Not tested |
| BT3 | `ProductLookupService` rate-limit retry path | Not tested |

### Frontend

| # | Area | Missing |
|---|------|---------|
| FT1 | `SearchStore` (core state machine) | No spec |
| FT2 | `ExtractionStore` (SSE ingestion, status keying by `whItemId`) | No spec |
| FT3 | Feature components (search, detail, item-research, item-annotation) | No specs |
| FT4 | `UaForwardingInterceptor` / `ServerErrorInterceptor` | No specs |

