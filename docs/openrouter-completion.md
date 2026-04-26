# OpenRouter — Completion Checklist

> Self-contained implementation guide. No codebase analysis needed.
> All file paths are relative to the project root.

**Status**: OpenRouter extraction calls work end-to-end via `AbstractLlmExtractionClient`.
The 4 gaps below are polish + one functional risk. Implement in one pass.

---

## Gap 1 — Startup API key log (diagnostic)

**File**: `backend/src/main/java/at/querchecker/api/config/ApiRestClientConfig.java`

The `groqRestClient()` bean logs the API key length/prefix at startup. `openRouterRestClient()` does not.

**Change**: Add the log line after reading the key.

```java
@Bean("openRouterRestClient")
public RestClient openRouterRestClient() {
    String apiKey = providerProperties.getProvider(Provider.OPENROUTER).getApiKey();
    log.info("[ApiRestClientConfig] OpenRouter API key: {}",
            apiKey == null || apiKey.isBlank() ? "MISSING/EMPTY" : apiKey.length() + " chars, prefix='" + apiKey.substring(0, Math.min(4, apiKey.length())) + "...'");
    return RestClient.builder()
            .requestFactory(createHttpRequestFactory())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("HTTP-Referer", "https://querchecker.at")
            .defaultHeader("X-Title", "Querchecker")
            .build();
}
```

Note: `X-Title: Querchecker` added here too (OpenRouter attribution header — recommended, not required).

---

## Gap 2 — getApiKeyInfo() override (diagnostic)

**File**: `backend/src/main/java/at/querchecker/api/extraction/OpenRouterExtractionClient.java`

The base class `getApiKeyInfo()` returns `"unknown"`. On 401/403 the log says `key=unknown`.
Groq overrides this. OpenRouter should too.

**Change**: Add `@Value` for the API key and override `getApiKeyInfo()`.

```java
@Component
public class OpenRouterExtractionClient extends AbstractLlmExtractionClient {

    private static final String ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";

    @Value("${querchecker.api.limits.openrouter.model:}")
    private String model;

    @Value("${querchecker.api.limits.openrouter.api-key:}")
    private String apiKey;

    public OpenRouterExtractionClient(@Qualifier("openRouterRestClient") RestClient restClient,
                                      ApiUsageLogService usageLogService,
                                      ProviderStatusService providerStatusService) {
        super(restClient, usageLogService, providerStatusService);
    }

    @Override
    public Provider getProvider() { return Provider.OPENROUTER; }

    @Override
    protected String getEndpointUrl() { return ENDPOINT; }

    @Override
    protected String getModel() { return model; }

    @Override
    protected String getApiKeyInfo() {
        if (apiKey == null || apiKey.isBlank()) return "MISSING/EMPTY";
        return apiKey.length() + " chars, prefix='" + apiKey.substring(0, Math.min(4, apiKey.length())) + "...'";
    }
}
```

---

## Gap 3 — response_format: json_object (functional risk)

**File**: `backend/src/main/java/at/querchecker/api/extraction/AbstractLlmExtractionClient.java`

`buildRequest()` always sends `"response_format": {"type": "json_object"}` when `expectJson=true`.
Groq and most modern models support this. Some OpenRouter-routed models do not — they return
HTTP 400, which becomes `Unreachable` + `RuntimeException` (run marked FAILED, no retry).

**Fix**: Add an overridable hook `supportsJsonResponseFormat()` in the abstract class.

**Step A** — Add hook to `AbstractLlmExtractionClient` (the method is `protected`, override in subclass):

```java
/**
 * Whether this provider supports response_format: json_object.
 * Groq: yes. OpenRouter: depends on model — default false to be safe.
 */
protected boolean supportsJsonResponseFormat() {
    return true;
}
```

**Step B** — Change `buildRequest()` to use the hook (replace the existing method):

```java
private ChatRequest buildRequest(String systemPrompt, String userPrompt, boolean expectJson, String model) {
    List<ChatRequest.Message> messages = new ArrayList<>();
    if (systemPrompt != null && !systemPrompt.isBlank()) {
        messages.add(new ChatRequest.Message("system", systemPrompt));
    }
    messages.add(new ChatRequest.Message("user", userPrompt));
    Map<String, String> responseFormat = (expectJson && supportsJsonResponseFormat())
        ? JSON_RESPONSE_FORMAT : null;
    return new ChatRequest(model, messages, 0.0, 1024, responseFormat);
}
```

**Step C** — Override in `OpenRouterExtractionClient`:

```java
@Override
protected boolean supportsJsonResponseFormat() {
    return false;
}
```

> **Why false for OpenRouter?** OpenRouter routes to different model backends depending on availability/pricing.
> Some underlying models ignore the parameter; some return 400. Without it, the model still produces JSON
> because the system prompt instructs it to — and `callLlmWithJsonRetry()` handles the rare non-JSON response
> with a retry. Safer to rely on the prompt instruction than on `response_format`.

---

## Gap 4 — No quota limit for OpenRouter (monitoring)

**File**: `backend/src/main/resources/application.yml` (or `config/querchecker.yml` if externalized)

OpenRouter is pay-per-use (no fixed free tier). The Usage Monitor shows calls/tokens but no quota bar.
This is acceptable for now — leave `freeLimit: 0` which is the default (no quota check).

If a spending limit is desired, add to the provider config:

```yaml
querchecker:
  api:
    limits:
      openrouter:
        model: meta-llama/llama-3.1-8b-instruct   # or your chosen model
        free-limit: 50000   # tokens — your chosen monthly budget
        free-limit-period: MONTHLY
        limit-unit: TOKENS
        period-start-day: 1
        alert-at-percent: 80
```

Without `free-limit`, quota bar stays hidden — no code change needed.

---

## Summary — files to touch

| File | Change |
|---|---|
| `ApiRestClientConfig.java` | Add startup log + `X-Title` header to `openRouterRestClient()` |
| `OpenRouterExtractionClient.java` | Add `@Value apiKey`, override `getApiKeyInfo()`, override `supportsJsonResponseFormat()` |
| `AbstractLlmExtractionClient.java` | Add `supportsJsonResponseFormat()` hook, update `buildRequest()` |
| `application.yml` | Optionally add `openrouter.free-limit` if budget tracking is wanted |
