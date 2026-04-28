package at.querchecker.api.extraction;

import at.querchecker.api.entity.RequestType;
import at.querchecker.api.exception.RateLimitException;
import at.querchecker.api.model.ChatRequest;
import at.querchecker.api.model.ChatResponse;
import at.querchecker.api.result.ApiCallResult;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.config.ProviderStatusService;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.model.SearchResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Gemeinsame Logik für LLM-basierte ExtractionClient-Implementierungen
 * (Groq, OpenRouter — beide nutzen OpenAI-kompatibles API-Format).
 */
@Slf4j
public abstract class AbstractLlmExtractionClient implements ExtractionClient {

  private static final ObjectMapper MAPPER = new ObjectMapper().configure(
    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
    false
  );

  protected final RestClient restClient;
  protected final ApiUsageLogService usageLogService;
  protected final ProviderStatusService providerStatusService;

  protected AbstractLlmExtractionClient(RestClient restClient, ApiUsageLogService usageLogService,
                                        ProviderStatusService providerStatusService) {
    this.restClient = restClient;
    this.usageLogService = usageLogService;
    this.providerStatusService = providerStatusService;
  }

  /** Vollständiger Endpunkt-URL für den jeweiligen Provider */
  protected abstract String getEndpointUrl();

  /** Kurze Diagnose-Info über den API-Key — für Logging bei Fehlern. */
  protected String getApiKeyInfo() {
    return "unknown";
  }

  /**
   * Whether this provider supports {@code response_format: json_object}.
   * Groq: yes. OpenRouter: no — underlying model availability varies, some return HTTP 400.
   * When false, JSON output is enforced via the system prompt instead.
   */
  protected boolean supportsJsonResponseFormat() {
    return true;
  }

  /** Primäres Modell (DL-Extraktion + erster QuickFacts-Lookup) */
  protected abstract String getModel();

  /**
   * Modell für den QuickFacts-Lookup nach Quellen-Index.
   * Standardmäßig immer das primäre Modell; Unterklassen können überschreiben.
   *
   * @param sourceIndex 0-based position of the source in the lookup loop
   */
  protected String getModelForLookup(int sourceIndex) {
      return getModel();
  }

  @Override
  public ApiCallResult<Void> testConnection() {
    ApiCallResult<ChatResponse> result = callLlm(
        RequestType.EXTRACTION, null,
        "You are a test assistant.",
        "Say: ok",
        false,
        getModel()
    );
    return switch (result) {
      case ApiCallResult.Success<ChatResponse> ignored    -> new ApiCallResult.Success<>(null);
      case ApiCallResult.RateLimited<ChatResponse> rl     -> new ApiCallResult.RateLimited<>(rl.retryAfterSeconds());
      case ApiCallResult.Unreachable<ChatResponse> u      -> new ApiCallResult.Unreachable<>(u.reason(), u.httpStatus());
      case ApiCallResult.Unavailable<ChatResponse> u      -> new ApiCallResult.Unavailable<>(u.reason(), u.httpStatus());
    };
  }

  @Override
  public String extractProductName(
    String title,
    String description,
    String categoryName,
    DlCategoryPrompt prompt
  ) {
    String userPrompt = prompt
      .getUserPrompt()
      .replace("{title}", sanitizeInput(title))
      .replace("{description}", sanitizeInput(truncate(description, 800)))
      .replace("{category}", categoryName);

    ApiCallResult<ChatResponse> result = callLlm(
      RequestType.EXTRACTION,
      null,
      prompt.getSystemPrompt(),
      userPrompt,
      false,
      getModel()
    );
    ChatResponse response = unwrapOrThrow(result);
    return response.firstChoice().trim();
  }

  @Override
  public ProductNameResult extractProductNameStructured(
    String title,
    String description,
    String categoryName,
    DlCategoryPrompt prompt
  ) {
    String userPrompt = prompt
      .getUserPrompt()
      .replace("{title}", sanitizeInput(title))
      .replace("{description}", sanitizeInput(truncate(description, 800)))
      .replace("{category}", categoryName);

    ApiCallResult<ChatResponse> result = callLlm(
      RequestType.EXTRACTION,
      null,
      prompt.getSystemPrompt(),
      userPrompt,
      true,
      getModel()
    );
    ChatResponse response = unwrapOrThrow(result);
    String raw = response.firstChoice().trim();

    try {
      ProductNameResult parsed = MAPPER.readValue(raw, ProductNameResult.class);
      log.debug(
        "extractProductNameStructured: extractedModel='{}', condensedSpec keys={}",
        parsed.extractedModel(),
        parsed.condensedSpec() != null ? parsed.condensedSpec().keySet() : "none"
      );
      return parsed;
    } catch (Exception e) {
      log.debug(
        "extractProductNameStructured: JSON parse failed ({}), treating as plain term",
        e.getMessage()
      );
    }
    // Fallback: treat as plain term (e.g. model returned non-JSON despite system prompt)
    if (raw.length() > 150) {
      String truncated = raw.substring(0, 150).trim();
      // Try to cut at sentence boundary for better results
      int lastDot = truncated.lastIndexOf('.');
      if (lastDot > 50) {
        // Only use sentence break if it's not too close to start
        truncated = truncated.substring(0, lastDot).trim();
      }
      log.warn(
        "extractProductNameStructured fallback: response too long ({} chars) — truncating to: '{}'",
        raw.length(),
        truncated
      );
      return new ProductNameResult(truncated, null);
    }
    return new ProductNameResult(raw, null);
  }

  @Override
  public QuickFactsResult extractQuickFacts(
    String lookupTerm,
    String categoryName,
    List<SearchResult> braveResults,
    List<String> mandatoryFields,
    DlCategoryPrompt prompt,
    String condensedSpecContext,
    int sourceIndex
  ) {
    String snippetsBlock = formatSnippets(braveResults);
    String condensedBlock = condensedSpecContext != null ? condensedSpecContext : "";
    String userPrompt = prompt
      .getUserPrompt()
      .replace("{lookupTerm}", sanitizeInput(lookupTerm))
      .replace("{category}", categoryName)
      .replace("{snippets}", snippetsBlock)
      .replace("{condensedSpec}", condensedBlock);

    String systemPrompt = buildSystemPrompt(prompt.getSystemPrompt(), mandatoryFields);
    String model = getModelForLookup(sourceIndex);

    QuickFactsResult result = callLlmWithJsonRetry(
      RequestType.EXTRACTION,
      lookupTerm,
      systemPrompt,
      userPrompt,
      model
    );
    return applyIcecatIdSafetyCheck(result, braveResults, lookupTerm);
  }

  @Override
  public QuickFactsResult extractQuickFactsFromText(
    String lookupTerm,
    String categoryName,
    String pageText,
    List<String> mandatoryFields,
    DlCategoryPrompt prompt,
    int sourceIndex
  ) {
    // Identisch zu extractQuickFacts(), aber {snippets} wird mit pageText befüllt
    String userPrompt = prompt
      .getUserPrompt()
      .replace("{lookupTerm}", sanitizeInput(lookupTerm))
      .replace("{category}", categoryName)
      .replace("{snippets}", pageText)
      .replace("{condensedSpec}", "");

    String systemPrompt = buildSystemPrompt(prompt.getSystemPrompt(), mandatoryFields);
    String model = getModelForLookup(sourceIndex);

    return callLlmWithJsonRetry(
      RequestType.EXTRACTION,
      lookupTerm,
      systemPrompt,
      userPrompt,
      model
    );
  }

  // --- private helpers ---

  /**
   * Appends mandatory fields as a rule to the system prompt.
   * If the list is empty, the system prompt is returned unchanged.
   */
  /**
   * LLM-Füllwerte, die inhaltlich "keine Angabe" bedeuten.
   * Solche Einträge werden aus quickFacts entfernt, damit sie nicht als
   * abgedeckte Pflichtfelder in der Qualitätsbewertung zählen.
   */
  private static final java.util.Set<String> FILLER_VALUES = java.util.Set.of(
    "unbekannt", "unknown", "-", "–", "n/a", "n.a.", "k.a.", "keine angabe",
    "nicht angegeben", "nicht verfügbar", "nicht bekannt"
  );

  /** Tech-Akronyme und Markennamen mit kanonischer Schreibweise. */
  private static final Map<String, String> CANONICAL_FORMS = Map.ofEntries(
    Map.entry("hdmi",         "HDMI"),
    Map.entry("usb",          "USB"),
    Map.entry("dvi",          "DVI"),
    Map.entry("vga",          "VGA"),
    Map.entry("displayport",  "DisplayPort"),
    Map.entry("thunderbolt",  "Thunderbolt"),
    Map.entry("bluetooth",    "Bluetooth"),
    Map.entry("wlan",         "WLAN"),
    Map.entry("lan",          "LAN"),
    Map.entry("oled",         "OLED"),
    Map.entry("qled",         "QLED"),
    Map.entry("lcd",          "LCD"),
    Map.entry("ips",          "IPS"),
    Map.entry("tn",           "TN"),
    Map.entry("va",           "VA"),
    Map.entry("hdr",          "HDR"),
    Map.entry("sdr",          "SDR"),
    Map.entry("uhd",          "UHD"),
    Map.entry("qhd",          "QHD"),
    Map.entry("fhd",          "FHD"),
    Map.entry("cpu",          "CPU"),
    Map.entry("gpu",          "GPU"),
    Map.entry("ram",          "RAM"),
    Map.entry("ssd",          "SSD"),
    Map.entry("hdd",          "HDD"),
    Map.entry("nvme",         "NVMe"),
    Map.entry("sata",         "SATA"),
    Map.entry("wifi",         "WiFi")
  );

  /**
   * Kapitalisiert jeden Wort-Teil eines Feldsschlüssels nach deutschen Konventionen.
   * Bekannte Akronyme/Markennamen werden in kanonischer Form geschrieben
   * (z.B. "hdmi" → "HDMI", "displayport" → "DisplayPort"),
   * alle anderen Wörter erhalten einen Großbuchstaben am Anfang.
   */
  private static String capitalizeFieldKey(String key) {
    if (key == null || key.isEmpty()) return key;
    // Split preserving separators (space, hyphen)
    String[] parts = key.split("(?<=[ -])|(?=[ -])");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.equals(" ") || part.equals("-")) {
        sb.append(part);
      } else {
        String lower = part.toLowerCase();
        String canonical = CANONICAL_FORMS.get(lower);
        if (canonical != null) {
          sb.append(canonical);
        } else if (!part.isEmpty()) {
          sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
      }
    }
    return sb.toString();
  }

  private String buildSystemPrompt(String baseSystemPrompt, List<String> mandatoryFields) {
    if (mandatoryFields == null || mandatoryFields.isEmpty()) {
      return baseSystemPrompt;
    }
    String fields = mandatoryFields.stream()
      .map(AbstractLlmExtractionClient::capitalizeFieldKey)
      .collect(Collectors.joining(", "));
    return baseSystemPrompt.stripTrailing()
      + "\n8. Pflichtfelder (MÜSSEN in quickFacts erscheinen, sofern im Text erkennbar): "
      + fields;
  }

  /**
   * Calls LLM and parses JSON response. If parsing fails, retries once with explicit
   * JSON-only instruction. Returns empty result if both attempts fail.
   * Throws RateLimitException on 429; throws RuntimeException on Unreachable/Unavailable.
   */
  private QuickFactsResult callLlmWithJsonRetry(
    RequestType requestType,
    String lookupTerm,
    String systemPrompt,
    String userPrompt,
    String model
  ) {
    ChatResponse response = unwrapOrThrow(callLlm(requestType, lookupTerm, systemPrompt, userPrompt, true, model));
    String firstAttempt = response.firstChoice();

    // Try parsing the initial response
    QuickFactsResult result = tryParseJson(firstAttempt);
    if (result != null) {
      return result; // Success on first try
    }

    // First parse failed — retry with explicit JSON-only instruction
    log.warn(
      "LLM response was invalid JSON (provider={}, model={}, requestType={}), retrying with explicit JSON instruction",
      getProvider(),
      model,
      requestType
    );
    String jsonOnlyPrompt =
      userPrompt + "\n\n⚠️ WICHTIG: Antworte NUR mit validem JSON, KEINE weiteren Erklärungen!";
    ChatResponse retryResponse = unwrapOrThrow(callLlm(requestType, lookupTerm, systemPrompt, jsonOnlyPrompt, true, model));
    String retryAttempt = retryResponse.firstChoice();

    result = tryParseJson(retryAttempt);
    if (result != null) {
      return result; // Success on retry
    }

    // Both attempts failed — give up and return empty
    log.error(
      "LLM response still invalid JSON after retry (provider={}, model={}, requestType={}), returning empty result",
      getProvider(),
      model,
      requestType
    );
    return new QuickFactsResult();
  }

  /**
   * Unwraps ApiCallResult.Success or throws the appropriate exception.
   * RateLimited → RateLimitException, Unreachable/Unavailable → RuntimeException.
   */
  private ChatResponse unwrapOrThrow(ApiCallResult<ChatResponse> result) {
    return switch (result) {
      case ApiCallResult.Success<ChatResponse> s -> s.value();
      case ApiCallResult.RateLimited<ChatResponse> rl ->
          throw new RateLimitException(rl.retryAfterSeconds(), getProvider(), getModel());
      case ApiCallResult.Unreachable<ChatResponse> u ->
          throw new RuntimeException("LLM Unreachable (status=" + u.httpStatus() + "): " + u.reason());
      case ApiCallResult.Unavailable<ChatResponse> u ->
          throw new RuntimeException("LLM Unavailable (status=" + u.httpStatus() + "): " + u.reason());
    };
  }

  /**
   * Attempts to parse JSON response. Returns null if parsing fails (caller can retry).
   */
  private QuickFactsResult tryParseJson(String json) {
    try {
      log.debug("QuickFacts raw LLM response (provider={}): {}", getProvider(), json);
      QuickFactsResult result = MAPPER.readValue(json, QuickFactsResult.class);
      if (result.getSources() == null) {
        result.setSources(new QuickFactsResult.Sources());
      }
      if (result.getQuickFacts() != null) {
        result.getQuickFacts().entrySet().removeIf(e ->
          e.getValue() == null || FILLER_VALUES.contains(e.getValue().toLowerCase(java.util.Locale.ROOT).trim())
        );
      }
      return result;
    } catch (IOException e) {
      // Return null to signal retry should be attempted; don't log here (let caller decide)
      return null;
    }
  }

  private ApiCallResult<ChatResponse> callLlm(
    RequestType requestType,
    String lookupTerm,
    String systemPrompt,
    String userPrompt,
    boolean expectJson,
    String model
  ) {
    ChatRequest request = buildRequest(systemPrompt, userPrompt, expectJson, model);

    // Estimate token count before calling LLM (for TPM monitoring)
    int estimatedInputTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
    log.debug(
      "[LLM] Estimated request tokens: {} (system: {}, user: {}), model={}",
      estimatedInputTokens,
      estimateTokens(systemPrompt),
      estimateTokens(userPrompt),
      model
    );
    log.trace("[LLM] System prompt:\n{}\n[LLM] User prompt:\n{}", systemPrompt, userPrompt);

    long start = System.currentTimeMillis();
    try {
      ChatResponse response = restClient
        .post()
        .uri(getEndpointUrl())
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(ChatResponse.class);
      long duration = System.currentTimeMillis() - start;

      if (response == null) response = new ChatResponse();
      int actualInputTokens = response.getTokensInput();
      int actualOutputTokens = response.getTokensOutput();
      log.debug(
        "[LLM] Actual tokens — Input: {} (estimate: {}), Output: {}, Duration: {}ms, model={}",
        actualInputTokens, estimatedInputTokens, actualOutputTokens, duration, model
      );
      usageLogService.log(getProvider(), requestType, lookupTerm, 200,
          response.getTokensInput(), response.getTokensOutput(), duration, model);
      providerStatusService.markValid(false);
      return new ApiCallResult.Success<>(response);
    } catch (HttpClientErrorException e) {
      long duration = System.currentTimeMillis() - start;
      int status = e.getStatusCode().value();
      if (status == 429) {
        var headers = e.getResponseHeaders();
        String retryAfterHeader = headers != null ? headers.getFirst("Retry-After") : null;
        int retryAfterSeconds = RateLimitException.parseRetryAfter(retryAfterHeader);
        log.warn("LLM rate limited (provider={}, model={}, retryAfter={}s, estimatedTokens={})",
            getProvider(), model, retryAfterSeconds, estimatedInputTokens);
        usageLogService.log(getProvider(), requestType, lookupTerm, status, estimatedInputTokens, null, duration, model);
        return new ApiCallResult.RateLimited<>(retryAfterSeconds);
      }
      String reason = e.getMessage();
      usageLogService.log(getProvider(), requestType, lookupTerm, status, null, null, duration, model);
      if (status == 401 || status == 403) {
        log.warn("LLM unavailable (provider={}, model={}, key={}, status={}): {}",
            getProvider(), model, getApiKeyInfo(), status, reason);
        providerStatusService.markUnavailable(false, reason, status);
        return new ApiCallResult.Unavailable<>(reason, status);
      }
      log.warn("LLM unreachable (provider={}, model={}, status={}): {}", getProvider(), model, status, reason);
      providerStatusService.markUnreachable(false, reason, status);
      return new ApiCallResult.Unreachable<>(reason, status);
    } catch (org.springframework.web.client.ResourceAccessException e) {
      long duration = System.currentTimeMillis() - start;
      String reason = "Timeout/Netzwerkfehler: " + e.getMessage();
      log.warn("LLM unreachable (provider={}, model={}): {}", getProvider(), model, reason);
      usageLogService.log(getProvider(), requestType, lookupTerm, 503, null, null, duration, model);
      providerStatusService.markUnreachable(false, reason, 503);
      return new ApiCallResult.Unreachable<>(reason, 503);
    } catch (RuntimeException e) {
      long duration = System.currentTimeMillis() - start;
      log.warn("LLM call failed (provider={}, model={}, key={}): {}",
          getProvider(), model, getApiKeyInfo(), e.getMessage());
      usageLogService.log(getProvider(), requestType, lookupTerm, 500, null, null, duration, model);
      throw e;
    }
  }

  private static final Map<String, String> JSON_RESPONSE_FORMAT = Map.of("type", "json_object");

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

  protected QuickFactsResult parseJson(String json) {
    try {
      log.debug("QuickFacts raw LLM response (provider={}): {}", getProvider(), json);
      QuickFactsResult result = MAPPER.readValue(json, QuickFactsResult.class);
      if (result.getSources() == null) {
        result.setSources(new QuickFactsResult.Sources());
      }
      return result;
    } catch (Exception e) {
      log.warn(
        "parseJson failed (provider={}, error={}, rawResponse={})",
        getProvider(),
        e.getMessage(),
        json
      );
      return new QuickFactsResult();
    }
  }

  private QuickFactsResult applyIcecatIdSafetyCheck(
    QuickFactsResult result,
    List<SearchResult> braveResults,
    String lookupTerm
  ) {
    if (result.getSources() == null) return result;
    String icecatId = result.getSources().getIcecatId();
    if (icecatId == null) return result;
    String icecatIdLower = icecatId.toLowerCase();

    // Step 1: ID must appear in at least one Brave URL that is an actual product page.
    // Asset/file URLs (objects.icecat.biz, PDFs, images) are rejected — they embed
    // file-object IDs that look like product IDs but are not.
    List<SearchResult> matchingResults = braveResults
      .stream()
      .filter(
        (r) ->
          r.getUrl() != null &&
          r.getUrl().toLowerCase().contains(icecatIdLower) &&
          isProductPageUrl(r.getUrl())
      )
      .toList();
    if (matchingResults.isEmpty()) {
      log.debug(
        "Safety check: icecatId='{}' not found in Brave product-page URLs — discarding",
        icecatId
      );
      result.getSources().setIcecatId(null);
      result.getSources().setSourceUrl(null);
      return result;
    }

    // Step 1b: Correct the icecatId to the actual product ID extracted from the URL.
    matchingResults
      .stream()
      .map((r) -> extractIcecatProductId(r.getUrl()))
      .filter((id) -> id != null && !id.equals(icecatId))
      .findFirst()
      .ifPresent((corrected) -> {
        log.debug(
          "Safety check: correcting icecatId '{}' → '{}' (from URL slug)",
          icecatId,
          corrected
        );
        result.getSources().setIcecatId(corrected);
      });

    // Step 2: Brand check — prevents picking up cross-linked products from Icecat sidebars.
    if (lookupTerm != null && !lookupTerm.isBlank()) {
      String brand = lookupTerm.trim().split("[\\s,]+")[0].toLowerCase();
      if (brand.length() > 2) {
        boolean brandMatch = matchingResults
          .stream()
          .anyMatch((r) -> {
            String title = r.getTitle() != null ? r.getTitle().toLowerCase() : "";
            String desc = r.getDescription() != null ? r.getDescription().toLowerCase() : "";
            return title.contains(brand) || desc.contains(brand);
          });
        if (!brandMatch) {
          log.debug(
            "Safety check: icecatId='{}' found in Brave URL but result doesn't mention brand '{}' — discarding",
            icecatId,
            brand
          );
          result.getSources().setIcecatId(null);
          result.getSources().setSourceUrl(null);
        }
      }
    }
    return result;
  }

  /**
   * Icecat product-page URL format: …/{category}-{EAN}-{name}-{productId}.html
   * The actual product ID is the LAST numeric segment before .html.
   */
  private static final Pattern ICECAT_PRODUCT_ID_PATTERN = Pattern.compile("-(\\d+)\\.html");

  private static String extractIcecatProductId(String url) {
    if (url == null) return null;
    Matcher m = ICECAT_PRODUCT_ID_PATTERN.matcher(url);
    String last = null;
    while (m.find()) last = m.group(1);
    return last;
  }

  private static boolean isProductPageUrl(String url) {
    if (url == null) return false;
    String lower = url.toLowerCase();
    if (lower.contains("objects.icecat.biz")) return false;
    if (
      lower.endsWith(".pdf") ||
      lower.endsWith(".jpg") ||
      lower.endsWith(".png") ||
      lower.endsWith(".jpeg") ||
      lower.endsWith(".webp")
    ) return false;
    return lower.contains("icecat.biz");
  }

  private String formatSnippets(List<SearchResult> results) {
    return results
      .stream()
      .map(
        (r) ->
          "---\nURL: " +
          r.getUrl() +
          "\nTitel: " +
          r.getTitle() +
          "\nSnippet: " +
          r.getDescription() +
          (r.getExtraSnippets() == null || r.getExtraSnippets().isEmpty()
            ? ""
            : "\nExtra: " + String.join(" | ", r.getExtraSnippets()))
      )
      .collect(Collectors.joining("\n"));
  }

  private static String truncate(String text, int maxChars) {
    if (text == null) return "";
    return text.length() <= maxChars ? text : text.substring(0, maxChars);
  }

  /**
   * Sanitizes user-supplied text before injecting it into LLM prompts.
   * Prevents the LLM from copying problematic characters into its JSON output:
   * - " (straight double quote / inch mark) → ″ (U+2033 DOUBLE PRIME): visually identical,
   *   the LLM still understands "21,5 Zoll" context but won't break JSON strings
   * - \ (backslash) → / : prevents accidental JSON escape sequences (\n, \t, etc.)
   */
  private static String sanitizeInput(String text) {
    if (text == null) return "";
    return text.replace('"', '″').replace('\\', '/');
  }

  /**
   * Rough token estimate: ~4 characters per token (typical for English/JSON).
   * Used for pre-call logging to monitor TPM usage patterns.
   * Not 100% accurate but good enough for capacity planning.
   */
  private static int estimateTokens(String text) {
    if (text == null || text.isEmpty()) return 0;
    // Count: roughly 4 characters per token, minimum 1
    return Math.max(1, text.length() / 4);
  }
}
