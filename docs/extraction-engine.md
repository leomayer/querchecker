# Extraction Engine — Architecture Reference

**Scope:** This document describes the backend pipeline that extracts a product name from a Willhaben listing. It covers the active models, how they are registered at startup, the execution queue, the run status machine, and the impact of extraction quality on the downstream cache. It does not cover what happens with the extracted term after it leaves this pipeline.

The extracted term pre-fills the KI-Suche search field and is the cache key for `ProductLookup`.

> For how that term is used downstream (Brave Search → LLM → Quick Facts), see 🤖 [KI-Produktanalyse](ki-produktanalyse.md).

---

## Active Models

| Model key | Type | execution_order | Notes |
|---|---|---|---|
| `groq` | Cloud API (Groq / OpenRouter) | 5 | Runs first — low latency, no local resources |
| `llama` | Local GGUF via llama.cpp | 10 | Runs second if configured |

`source-model: llama` in `application.yml` — only the llama model's term is broadcast as `suggestedTerm` (pre-fills the search field). The groq model broadcasts without `suggestedTerm`.

---

## Conditional Model Registration

Using `@Component` would instantiate all models at startup regardless of mode, loading GGUF files unconditionally. Instead, `DlModelConfiguration` registers models on `ApplicationReadyEvent`:

- **API mode** (`querchecker.llm.mode=API`): only `LlmApiExtractionModel` — no GGUF files loaded
- **LOCAL mode**: only models marked `active=true` in the DB

The server starts even without any GGUF files present. Local models are only loaded when they are actually active.

---

## Queue Architecture

All extraction runs execute sequentially — one at a time, globally. A single-threaded `LinkedBlockingDeque` + `ThreadPoolExecutor(1,1)` serializes all runs.

**Why not parallel?** Local models need full GPU; parallel runs would throttle each other. Cloud models (Groq) are subject to rate limits.

> **Trade-off:** Strict serialization is the right call for local models — the GPU is a hard, exclusive resource. For cloud-only deployments (API mode), it is more conservative than necessary: Groq and OpenRouter handle low concurrency (2–3 simultaneous requests) well within their rate limits, so sequential execution adds latency without providing real protection. The current design applies the same constraint uniformly to both modes for simplicity. A small thread pool with rate-limit backoff would be a better fit for API mode, but the practical impact is low given typical single-listing browsing patterns.

**Priority:** New requests go to the front (`addFirst()`). Sort is by `executionOrder` DESC → lowest value = highest priority at front. The user gets faster feedback for the listing they currently have open.

**Queue overflow:** When the queue depth exceeds the configured limit (`AppConfig "dl.queue.limit"`, default 10 — waiting tasks only, not the active run), the oldest waiting task is evicted via `pollLast()`. The evicted `ExtractionTask` gets status `CANCELLED` and is persisted. This is not an error. When the listing is reopened, the scheduler checks for an existing run with status `DONE / INIT / PENDING` — CANCELLED is deliberately excluded from this guard, so a new run is created automatically without extra retry logic.

---

## Status Machine (`DlExtractionRun.status`)

```
INIT ──→ PENDING → DONE
                 ↘ FAILED
INIT → NO_IMPLEMENTATION   (terminal — no ExtractionModel bean for this model name)
INIT → CANCELLED            (queue overflow)
DONE/PENDING/INIT → RE_EVALUATE → INIT  (term cleared before re-run)
```

`RE_EVALUATE` exists for cases where a previously extracted term needs to be re-derived without losing the full run history.

---

## Term Quality and Cache Efficiency

The quality of the extracted term has a direct impact on the `ProductLookup` cache. The cache key is a plain string match on `lookupTerm` — inconsistent extraction means no cache hits.

Cloud models (Groq / OpenRouter) produce consistent, normalized product names. Local models can extract the same product differently across runs, causing every run to create a new cache entry and a new Brave API call.

For production use, always set `querchecker.llm.external-provider: GROQ` or `OPENROUTER`. Local models are viable for offline development but degrade cache efficiency significantly.

> Full analysis with examples: 💻 [Lokales LLM degradiert die Cache-Effizienz](local-models.md#lokales-llm-degradiert-die-cache-effizienz)
