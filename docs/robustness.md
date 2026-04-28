# Robustness & Error Handling

How Querchecker handles failures gracefully. For ops teams and developers managing production deployments.

---

## API Rate-Limiting

### Groq HTTP 429 Handling

**Scenario**: Groq rejects request with HTTP 429 "Too Many Requests" (6000 TPM limit exceeded).

**Backend Response**:

1. `AbstractLlmExtractionClient` catches `HttpClientErrorException` (status 429)
2. Parses `Retry-After` header → `RateLimitException(retryAfterSeconds, provider)`
3. **For DL extraction** (`DlExtractionService`):
   - If `retryAfterSeconds ≤ 20`: `Thread.sleep(retryAfter + 500ms)` → retry once inline
   - If `> 20`: Save with `FAILED` status
4. **For KI-Suche** (`ProductLookupService`):
   - If `retryAfterSeconds ≤ 20`: Schedule async retry via `CompletableFuture.delayedExecutor()`
   - Broadcast SSE `lookup-result` event with `RATE_LIMITED` status
   - If `> 20`: Fall back to `bestPartial` cached result (from earlier Brave search) if available
   - If no cached result: Save with `RATE_LIMITED` status

**Frontend UX**:
- SSE event triggers re-fetch automatically
- User sees "Loading..." → eventually results appear (or "Error — try later")
- No page reload, no lost context

### Brave Search HTTP 429

**Similar handling** in `BraveWebSearchService`:
- Throws `RateLimitException(retryAfterSeconds, BRAVE)`
- Caught by `ProductLookupService` → triggers retry cascade

---

## LLM Response Errors

### Invalid JSON

**Scenario**: LLM returns malformed JSON (e.g., extra commas, missing quotes).

**Handling**:

1. First attempt: Standard `ObjectMapper.readValue()`
2. If fails: `tryParseJson()` — try alternative parsing logic
3. If still fails: Mark run `FAILED` (for DL) or fall back to next source (for lookup)

### Hallucinated URLs & IDs

**Scenario**: LLM invents fake icecatId or sourceUrl that doesn't exist.

**Handling** (`UrlValidator`):

- `resolveSourceUrl(llmUrl, braveResults)` — validate LLM URL against actual Brave results
  - If match found: use it
  - Otherwise: fall back to top Brave URL
- `resolveIcecatId(llmId, braveResults)` — check if ID appears in any Brave result
- `matchesExpectedPattern(url, sourceType)` — regex validation per source type
  - ICECAT: `icecat.biz/p/[name]-[id].html`
  - GSMARENA: `gsmarena.com/[name]-[id].php`
  - FLATPANELSHD: `flatpanelshd.com/[\w\-]+.php`

### Filler Values in QuickFacts

**Scenario**: LLM extracts "unbekannt", "-", "n/a", "unknown" as a specification value.

**Handling** (`AbstractLlmExtractionClient`):

```java
Set<String> FILLER_VALUES = Set.of("unbekannt", "-", "n/a", "unknown", "not specified", ...);

// After JSON parsing, strip fillers:
quickFacts.entrySet().removeIf(e -> FILLER_VALUES.contains(e.getValue().toLowerCase()));
```

**Benefit**: Filler values don't count as "covered" in quality evaluation (GOOD/PARTIAL/EMPTY).

### Inch-Mark JSON Errors

**Scenario**: LLM writes `"24""` instead of `"24 Zoll"` (common with non-ASCII quotes).

**Handling** (`AbstractLlmExtractionClient.sanitizeLlmOutput()`):

```java
// Before JSON parsing:
rawOutput = rawOutput.replaceAll("(\\d+)\"\"([,\\}])", "$1 Zoll$2");
```

Also **documented in PRODUCT_NAME system prompt** to prevent at the source:
> "Verwende einfache ASCII-Leerzeichen zwischen Zahl und Einheit, nie Gänsefüßchen."

---

## Extraction Quality Evaluation

**`ExtractionQualityEvaluator`** assigns a grade to each lookup result:

| Grade | Condition | Action |
|-------|-----------|--------|
| `GOOD` | ≥60% SYSTEM fields populated + icecatId valid (for ICECAT) | Stop, persist COMPLETE |
| `PARTIAL` | >0% but <60% SYSTEM fields | Try next source |
| `EMPTY` | 0% SYSTEM fields | Try next source |
| `FAILED_NO_CRITERIA` | No SYSTEM fields configured for category | Try next source |

**System fields**: Named attributes (RAM, CPU, Display Size, etc.) from `CategorySpecPreference`.
**User fields**: Search keywords ("OLED", "Core i7") — excluded from quality check.

---

## Cache & TTL Strategy

`ProductLookup` caches lookup results by `lookupTerm`:

| Status           | In DB?       | Verhalten                                                               | Konfiguration                                      |
| ---------------- | ------------ | ----------------------------------------------------------------------- | -------------------------------------------------- |
| `COMPLETE`       | ✅ permanent | quickFacts sofort anzeigen — kein API-Call                              | —                                                  |
| `FAILED`         | ✅ mit TTL   | kein API-Call solange TTL aktiv; danach erneute Suche                   | `product.lookup.failed.ttl.hours` (default 24h)    |
| `ERROR`          | ✅ mit TTL   | kein API-Call solange TTL aktiv; danach erneute Suche                   | `product.lookup.error.ttl.minutes` (default 10min) |
| `QUOTA_EXCEEDED` | ✅           | weiter zu Kontingent-Check; wird überschrieben wenn Periode neu startet | —                                                  |
| `RATE_LIMITED`   | ❌           | Backend plant async Retry (wenn ≤20s); SSE-Push bei Ergebnis            | —                                                  |
| `NO_SOURCES`     | ❌           | Quellen werden bei jedem Aufruf neu geprüft (virtual status)            | —                                                  |

`COMPLETE` has no TTL — product specs don't change post-release. Deleted only via Settings cleanup or manual SQL.

`NO_SOURCES` occurs when the listing's category has no `CategorySearchSource` entries (or all with `lookupEnabled=false`), or when `WhListing.whCategory == null`.

If no entry exists for the `lookupTerm`, the full pipeline runs: quota check → load sources → web search → LLM.

---

## Quota & Limits

### Provider Quotas

Configured per provider in `application.yml`:

```yaml
querchecker:
  api:
    providers:
      brave:
        free-limit: 1000
        free-limit-period: MONTHLY
      groq:
        free-limit: 25000
        free-limit-period: DAILY
      icecat:
        free-limit: 0  # No quota (free access)
```

### Quota Checks

**`QuotaService.checkQuota(provider)`** returns:

| Status | When | Action |
|--------|------|--------|
| `OK` | Usage < 80% | Proceed |
| `WARNING` | 80% ≤ Usage < 100% | Proceed + show icon in Settings |
| `BLOCKED` | Usage ≥ 100% | Reject, return `QUOTA_EXCEEDED` |

### Monitoring

**Settings → Usage Monitor**:
- Provider cards with current period usage
- Call counts (this period + today)
- Token budgets (IN / OUT)
- Visual gradient bar (green → yellow → red)

---

## DL Extraction Queue

### Overflow Handling

**Scenario**: User rapidly clicks detail panel (5 listings in quick succession).

**Queue Behavior**:

1. First extraction: `INIT` → queued
2. Second click (400ms debounce): scheduling delayed, extraction still running
3. Third click: new `INIT` run added to queue (now 2 waiting)
4. Fourth click: new `INIT` run (now 3 waiting)
5. Fifth click: exceeds limit (default 10) → `pollLast()` removes lowest-priority → marked `CANCELLED` + saved

**Resolution**: User re-opens the cancelled listing → `openDetail()` calls `scheduleExtraction()` → new `INIT` run created (retry).

### Duplicate Detection

**`existsByItemTextAndModelConfigAndStatusIn([DONE, INIT, PENDING])`**:
- If found: skip (don't queue again)
- **Exception**: `CANCELLED` status NOT in skip list → creates new `INIT` (retry)

Why? CANCELLED runs must be retryable without user intervention.

---

## Server Restarts & SSE Reconnection

### Soft Restart (No Page Reload)

**Frontend**:

1. EventSourceServerService detects SSE close
2. Calls `health.notifyServerError()` → polling switches to rapid 3s retry
3. When backend comes back: SSE re-connects automatically
4. Token validation: new token issued, SSE event updates frontend state
5. MatSnackBar: "Server neugestartet — Verbindung wiederhergestellt"

**Benefit**: User keeps context (scroll position, search filters, form state).

### 40-Second Stale Watchdog

**Scenario**: Network hangs (no data, no error).

**Handling** (`EventSourceServerService`):
- SSE idle for >40s → close connection + reconnect
- Prevents half-dead connections from blocking new messages

---

## Database Resilience

### Connection Pooling

Spring Boot default HikariCP with reasonable defaults:
- Max pool size: 10
- Idle timeout: 10 minutes
- Connection timeout: 30 seconds

### Transaction Handling

- Read-only queries use `@Transactional(readOnly = true)` (hint to DB)
- Write operations auto-wrapped in `@Transactional`
- No manual rollback logic (Spring handles exceptions)

### Enum Storage

**Stored as VARCHAR (not PG native enum)** → safer schema migrations (Flyway-compatible).

Example: `ExtractionStatus` enum:
```sql
INIT | PENDING | DONE | FAILED | NO_IMPLEMENTATION | RE_EVALUATE | CANCELLED
```

---

## Logging & Observability

### TRACE Level

Enable for troubleshooting:

```bash
export LOGGING_LEVEL_AT_QUERCHECKER=TRACE
```

**`AbstractLlmExtractionClient`** logs full interpolated prompts at TRACE level:
```
[TRACE] System Prompt: [full prompt text]
[TRACE] User Prompt: [full prompt text]
```

Useful for debugging LLM responses.

### Structured Logging

All services log relevant context:
- `whListingId`, `listingId`, `lookupTerm`, `provider`, `retryAfterSeconds`, etc.

Easier to trace cross-service request flows.

---

## Monitoring Checklist for Ops

- [ ] **SSE stream health**: Check backend logs for `SseHub.broadcast()` calls
- [ ] **API quota usage**: Monitor Settings page or `ApiUsageLog` table
- [ ] **Rate limits**: Alert when `Retry-After` > 5 minutes (provider overload)
- [ ] **Queue depth**: Monitor `DlExtractionRun` status = INIT (→ how many waiting?)
- [ ] **Cache hit rate**: Compare `COMPLETE` / `FAILED` lookups (high reuse = good)
- [ ] **Error rate**: Track FAILED extractions by model (degraded LLM quality?)
- [ ] **Database size**: `ProductLookup` table grows over time (monitor retention)

