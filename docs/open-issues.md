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

---

## Provider Setup Feature (provider-config.md blocks)

The setup wizard UI exists but several blocks are partially or not yet implemented.
Full spec in `docs/concepts/provider-config.md`.

| # | Block | Status | What's needed |
|---|-------|--------|---------------|
| P1 | Block B — ProviderStatusStore + Popup + Badge | Partial | `ProviderStatusStore` (read-only, SSE-driven). Hash-based popup on UNCONFIGURED. Badge on Settings button with Warning/Error color. |
| P3 | Block E — Test-Button + Lazy Validation feedback | Open | Test-Button in Settings (remote providers only, CONFIGURED/UNREACHABLE/UNAVAILABLE). Lazy validation: first call failure → SSE event → frontend state update |

---

## Testing Gaps

### Backend

| # | Area | Missing |
|---|------|---------|
| BT1 | `WhListingService` / `WhSearchService` | No test files visible |
| BT2 | `DlExtractionController` (SSE broadcasting) | Not tested |
| BT3 | `ProductLookupService` rate-limit retry path | Not tested |
| BT4 | OpenRouter extraction path (OR1–OR3: startup log, `getApiKeyInfo`, `supportsJsonResponseFormat=false`) | Implemented but **untested** — needs live OpenRouter API key |

### Frontend

| # | Area | Missing |
|---|------|---------|
| FT1 | `SearchStore` (core state machine) | No spec |
| FT2 | `ExtractionStore` (SSE ingestion, status keying by `whItemId`) | No spec |
| FT3 | Feature components (search, detail, item-research, item-annotation) | No specs |
| FT4 | `UaForwardingInterceptor` / `ServerErrorInterceptor` | No specs |

