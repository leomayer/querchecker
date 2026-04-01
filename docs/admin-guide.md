# Querchecker Admin Guide

Configuration, maintenance, and operational procedures for Querchecker.

---

## Category Lookup Configuration

### Disabling Lookups for Specific Categories

Categories can be excluded from LLM extraction (spec lookup) in two ways:

#### 1. Implicit Disable: No Entry (Recommended)

If a category has **no entry** in the `category_search_source` table:

- Lookups return `NO_SOURCES` status
- No API calls are made
- Users see: "KI-Suche nicht konfiguriert"

#### 2. Explicit Disable: `lookupEnabled=false`

Add an entry in `category_search_source` with `lookupEnabled=false`:

- Same result as implicit disable (`NO_SOURCES`)
- Documents the intent clearly
- Easier to toggle back on without re-creating configuration
- Useful for temporarily disabling a source

**Implementation**: `CategorySearchSourceService.findForCategory()` filters by `isLookupEnabled()` before returning sources to the lookup pipeline.

#### Hierarchy & Inheritance

- **Level check**: Categories can inherit lookup sources from parent categories (configured via `inheritFromParent=true`)
- **Explicit disable takes precedence**: If a category has an entry with `lookupEnabled=false`, parent sources are not used
- See: `CategorySearchSourceService` javadoc for full traversal logic

### Managing Category Search Sources

Table: `category_search_source`

| Field                 | Type    | Purpose                                                  |
| --------------------- | ------- | -------------------------------------------------------- |
| `wh_category_id`      | FK      | Category reference (nullable = shared/inherited source)  |
| `site_domain`         | String  | Search domain (e.g., `icecat.biz`, `gsmarena.com`)       |
| `site_label`          | String  | Display name in UI                                       |
| `source_type`         | Enum    | `ICECAT` \| `FLATPANELSHD` \| `GSMARENA` \| `GENERIC`    |
| `lookup_enabled`      | Boolean | Enable/disable this source                               |
| `active`              | Boolean | Soft-delete flag                                         |
| `priority`            | Integer | Order of sources (ascending)                             |
| `search_result_count` | Integer | Number of results from web search (default 10)           |
| `query_excludes`      | TEXT[]  | PostgreSQL array of exclusion keywords for Brave queries |
| `inherit_from_parent` | Boolean | Allow child categories to use this source as fallback    |

**Initial Data**: Populated via `CategorySearchSourceSeeder` (additive upsert on startup). Re-seed by:

```sql
DELETE FROM category_search_source;
-- Restart application to re-run seeder
```

---

## Token Management & Brave Search Optimization

### Overview

The LLM API (Groq) has **6000 TPM (Tokens Per Minute)** limit. Full Brave search results can exceed this, causing HTTP 413 "Payload Too Large" errors.

### Snippet Truncation Strategy (2026-03-31)

Brave search results are automatically truncated **before** being sent to the LLM:

| Parameter               | Value     | Rationale                                       |
| ----------------------- | --------- | ----------------------------------------------- |
| Max search results      | 5         | Reduces volume; top results are highest quality |
| Description truncation  | 250 chars | First 250 chars contain title + initial context |
| Max snippets per result | 7         | Provides good coverage without excess           |
| Snippet truncation      | 250 chars | First 250 chars contain key specifications      |

**Token Budget Calculation**:

- Per result: 250 (desc) + 7×250 (snippets) ≈ 2025 chars ≈ 505 tokens
- All 5 results: ≈ 2525 tokens
- With prompt (~600 tokens): ≈ 3125 tokens total
- **Headroom**: ≈ 2875 tokens under 6000 TPM limit ✓

**Why This Works**:

- First 200-250 characters typically contain most important info (title, context, key specs)
- Longer snippets rarely add significant value for LLM extraction
- Simple character-based truncation is maintainable and predictable
- No need for complex token-counting library

**Implementation**: `BraveWebSearchService.extractResults()` with helper methods:

- `truncateString(String text, int maxLength)` — cuts at length + "..."
- `truncateSnippets(List<String> snippets, int maxSnippets, int maxCharsPerSnippet)` — limits array + truncates each

**Google Discovery Search**: Unaffected. Returns single `snippets` string (not array), no `extraSnippets` field.

### Monitoring & Troubleshooting

**Symptom**: HTTP 413 errors with "rate_limit_exceeded" or "Payload Too Large"

**Root Cause**:

- Prompts too large, OR
- Search results still exceeding limits after truncation, OR
- Multiple concurrent requests hitting TPM burst limit

**Actions**:

1. Check logs for actual token count in error message
2. If consistently >5500 tokens for search results:
   - Reduce `MAX_RESULTS` (5 → 4)
   - Reduce `MAX_SNIPPET_LENGTH` (250 → 200)
   - Reduce `MAX_SNIPPETS_PER_RESULT` (7 → 5)
3. These parameters are hardcoded in `BraveWebSearchService` but easy to extract to `AppConfig` if tuning becomes frequent

---

## Rate Limiting & Retry Behavior

### Frontend Display

When LLM lookup hits rate limits, users see:

- **`RATE_LIMITED` status**: "Rate-Limit erreicht — verfügbar in ~Xs"
- **Retry button**: Enabled/disabled based on countdown
- **Auto-retry**: Backend schedules async retry; results push via SSE `lookup-result` event

### Backend Handling

- **Groq HTTP 429 (Too Many Requests)**: Caught, retry scheduled if ≤20s
- **Groq HTTP 413 (Payload Too Large)**: Treated as rate limit (Groq returns this for TPM exceedance)
- **Brave HTTP 429**: Same retry logic
- **Retry mechanism**: `SearchResultCacheService` caches Brave results; on retry, same cached results are used (no new web search)

---

## Category Preferences (Field Management)

Table: `category_spec_preference` and `category_spec_preference_field`

SYSTEM fields: Mandatory for LLM extraction; used in quality evaluation
USER fields: Search keywords; not evaluated for quality

**Recursive inheritance**: If a category has no preference entry, the system checks parent categories up the tree.

Management via: Settings UI (planned) or direct DB insert.

---

## Logging & Diagnostics

### Key Log Patterns

| Pattern                                                        | Source                      | Meaning                               |
| -------------------------------------------------------------- | --------------------------- | ------------------------------------- |
| `[ProductLookupService] === LOOKUP START ===`                  | ProductLookupService        | Spec lookup initiated                 |
| `[ProductLookupService] Found X sources for category`          | ProductLookupService        | CategorySearchSource query result     |
| `Rate limit detected in error response`                        | ProductLookupService        | HTTP 413 with "rate_limit_exceeded"   |
| `[CategorySearchSourceService] Searching sources for category` | CategorySearchSourceService | Lookup source discovery (DEBUG level) |
| `Brave search rate limited — retryAfter=Xs`                    | BraveWebSearchService       | HTTP 429 from Brave                   |

Enable DEBUG logging in `application.yml`:

```yaml
logging:
  level:
    at.querchecker: DEBUG
```

---

## Database Maintenance

### Backup Before Changes

Always backup `category_search_source` and `category_spec_preference*` tables before bulk changes:

```bash
pg_dump -t category_search_source -t category_spec_preference -t category_spec_preference_field \
  postgres://user:pass@host/querchecker > backup.sql
```

### Bulk Updates

Example: Disable all FLATPANELSHD sources:

```sql
UPDATE category_search_source
SET lookup_enabled = false
WHERE source_type = 'FLATPANELSHD' AND lookup_enabled = true;
```

Example: Restore:

```sql
UPDATE category_search_source
SET lookup_enabled = true
WHERE source_type = 'FLATPANELSHD' AND lookup_enabled = false;
```

---

## Environment Variables

| Variable                  | Purpose              | Default          |
| ------------------------- | -------------------- | ---------------- |
| `QUERCHECKER_DB_HOST`     | PostgreSQL host:port | `localhost:5432` |
| `QUERCHECKER_DB_USER`     | DB username          | `myuser`         |
| `QUERCHECKER_DB_PASSWORD` | DB password          | `mypassword`     |

Prod config: See `application-prod.yml`

---

## See Also

- **Technical Details**: `.zed/prompts/project-knowledge/backend.md`
- **Deployment**: `docs/todo-deployment.md`
- **Development**: `docs/DEVELOPMENT.md` (if exists)
