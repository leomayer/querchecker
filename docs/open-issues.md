# Open Issues — Querchecker v0.2.0

> Consolidated from: todo-quecker-1.md, openrouter-completion.md, code analysis.
> Priority: H = blocking / M = important / L = polish/nice-to-have

---

## Features (Product)

| #   | Title                                        | Priority | Notes                                                                                                                                                                       |
| --- | -------------------------------------------- | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F1  | Similar listings                             | M        | Clientside only — Substring match in SearchStore `patchedListings` by extracted model name. Active listing excluded. No API call.                                           |
| F2  | Mobile layout                                | L        | No design spec yet                                                                                                                                                          |
| F3  | More platforms (eBay Kleinanzeigen, Shpock)  | L        | Ideation only                                                                                                                                                               |
| F4  | Market price comparison (multi-shop)         | L        | Brave per-shop queries (`site:geizhals.at`, `site:idealo.at`, `site:amazon.de`) → LLM extracts best price + URL. High quota cost. Geizhals deep-link is current workaround. |
| F5  | Leave search field open after LLM extraction | L        | After LLM finds a search term, keep the field editable instead of auto-filling. Useful when LLM term needs manual adjustment before lookup.                                 |
| F6  | Single-User / Multi-User concept             | L        | Ideation only — user management, per-user key management, per-user quota. Currently single-user with HTTP Basic Auth.                                                       |

---

## Technical Debt / Refactoring

| #   | Title                                                      | Priority | Notes                                                                                                                                                                                                                                                                    |
| --- | ---------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| T1  | Category lookup config → Settings UI                       | M        | `category_search_source` currently seeded from Java `CategorySearchSourceDefinitions` — additive only, changes require DB DELETE + restart. Goal: CRUD in Settings/Provider. REST: GET/POST/DELETE for `CategorySearchSource`.                                           |
| T2  | Category prompt config → Settings UI                       | M        | Same problem as T1 for `dl_category_prompt` / `DlCategoryPromptDefinitions`.                                                                                                                                                                                             |
| T3  | Local deployment for end users                             | M        | Docker single-command for non-technical users (Mac/Windows/Linux). Remove Traefik labels, add port `14072:80`, set `QUERCHECKER_DL_GPU_LAYERS=0`. nginx SSE fix required (`proxy_buffering off` for `/api/`). Details: `docs/admin-guide.md#local-deployment-end-users`. |
| T5  | Developer setup documentation missing                      | M    | Clone → configure API keys → first run in dev mode not documented anywhere publicly. Lives only in internal notes. Goal: `docs/dev-setup.md` or a "Development" section in `admin-guide.md`. Should cover: Docker DB start, backend `mvn spring-boot:run`, frontend `npm start`, `secrets.yml` setup, generate-api workflow. |
| ✅ T4  | Retry on transient network error in `ProductLookupService` | Done | Automatic retry on `ResourceAccessException` (timeout/connection-refused) with exponential backoff: 500ms, 1s. Max 2 retries (3 total attempts). Saves ERROR status only if all retries exhausted. Tests: `lookup_retriesOnTransientTimeout_succeedsOnSecondAttempt`, `lookup_savesErrorAfterExhaustingRetries_onPersistentTimeout`. |

---

## Testing Gaps

### Backend

| #   | Area                                                                                          | Missing                                                      |
| --- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| BT1 | `WhListingService` / `WhSearchService`                                                        | No test files visible                                        |
| BT2 | `DlExtractionController` (SSE broadcasting)                                                   | Not tested                                                   |
| BT3 | `ProductLookupService` rate-limit retry path                                                  | Not tested                                                   |
| BT4 | OpenRouter extraction path (startup log, `getApiKeyInfo`, `supportsJsonResponseFormat=false`) | Implemented but **untested** — needs live OpenRouter API key |

### Frontend

| #   | Area                                                                | Missing  |
| --- | ------------------------------------------------------------------- | -------- |
| FT1 | `SearchStore` (core state machine)                                  | No spec  |
| FT2 | `ExtractionStore` (SSE ingestion, status keying by `whItemId`)      | No spec  |
| FT3 | Feature components (search, detail, item-research, item-annotation) | No specs |
| FT4 | `UaForwardingInterceptor` / `ServerErrorInterceptor`                | No specs |
