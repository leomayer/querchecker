package at.querchecker.api.extraction;

import at.querchecker.api.entity.RequestType;
import at.querchecker.api.exception.RateLimitException;
import at.querchecker.api.model.ChatRequest;
import at.querchecker.api.model.ChatResponse;
import at.querchecker.api.service.ApiUsageLogService;
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

  protected AbstractLlmExtractionClient(RestClient restClient, ApiUsageLogService usageLogService) {
    this.restClient = restClient;
    this.usageLogService = usageLogService;
  }

  /** Vollständiger Endpunkt-URL für den jeweiligen Provider */
  protected abstract String getEndpointUrl();

  /** Kurze Diagnose-Info über den API-Key — für Logging bei Fehlern. */
  protected String getApiKeyInfo() {
    return "unknown";
  }

  /** Modellname aus der Provider-Konfiguration */
  protected abstract String getModel();

  @Override
  public String extractProductName(
    String title,
    String description,
    String categoryName,
    DlCategoryPrompt prompt
  ) {
    String userPrompt = prompt
      .getUserPrompt()
      .replace("{title}", title)
      .replace("{description}", truncate(description, 800))
      .replace("{category}", categoryName);

    ChatResponse response = callLlm(
      RequestType.EXTRACTION,
      null,
      prompt.getSystemPrompt(),
      userPrompt
    );
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
      .replace("{title}", title)
      .replace("{description}", truncate(description, 800))
      .replace("{category}", categoryName);

    ChatResponse response = callLlm(
      RequestType.EXTRACTION,
      null,
      prompt.getSystemPrompt(),
      userPrompt
    );
    String raw = response.firstChoice().trim();

    try {
      ProductNameResult parsed = MAPPER.readValue(sanitizeRawJson(raw), ProductNameResult.class);
      if (parsed.extractedModel() != null && !parsed.extractedModel().isBlank()) {
        log.debug(
          "extractProductNameStructured: extractedModel='{}', condensedSpec keys={}",
          parsed.extractedModel(),
          parsed.condensedSpec() != null ? parsed.condensedSpec().keySet() : "none"
        );
        return parsed;
      }
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
    String condensedSpecContext
  ) {
    String snippetsBlock = formatSnippets(braveResults);
    String condensedBlock = condensedSpecContext != null ? condensedSpecContext : "";
    String userPrompt = prompt
      .getUserPrompt()
      .replace("{lookupTerm}", lookupTerm)
      .replace("{category}", categoryName)
      .replace("{snippets}", snippetsBlock)
      .replace("{condensedSpec}", condensedBlock);

    String systemPrompt = buildSystemPrompt(prompt.getSystemPrompt(), mandatoryFields);

    QuickFactsResult result = callLlmWithJsonRetry(
      RequestType.EXTRACTION,
      lookupTerm,
      systemPrompt,
      userPrompt
    );
    return applyIcecatIdSafetyCheck(result, braveResults, lookupTerm);
  }

  @Override
  public QuickFactsResult extractQuickFactsFromText(
    String lookupTerm,
    String categoryName,
    String pageText,
    List<String> mandatoryFields,
    DlCategoryPrompt prompt
  ) {
    // Identisch zu extractQuickFacts(), aber {snippets} wird mit pageText befüllt
    String userPrompt = prompt
      .getUserPrompt()
      .replace("{lookupTerm}", lookupTerm)
      .replace("{category}", categoryName)
      .replace("{snippets}", pageText)
      .replace("{condensedSpec}", "");

    String systemPrompt = buildSystemPrompt(prompt.getSystemPrompt(), mandatoryFields);

    return callLlmWithJsonRetry(
      RequestType.EXTRACTION,
      lookupTerm,
      systemPrompt,
      userPrompt
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
   */
  private QuickFactsResult callLlmWithJsonRetry(
    RequestType requestType,
    String lookupTerm,
    String systemPrompt,
    String userPrompt
  ) {
    ChatResponse response = callLlm(requestType, lookupTerm, systemPrompt, userPrompt);
    String firstAttempt = response.firstChoice();

    // Try parsing the initial response
    QuickFactsResult result = tryParseJson(firstAttempt);
    if (result != null) {
      return result; // Success on first try
    }

    // First parse failed — retry with explicit JSON-only instruction
    log.warn(
      "LLM response was invalid JSON (provider={}, requestType={}), retrying with explicit JSON instruction",
      getProvider(),
      requestType
    );
    String jsonOnlyPrompt =
      userPrompt + "\n\n⚠️ WICHTIG: Antworte NUR mit validem JSON, KEINE weiteren Erklärungen!";
    ChatResponse retryResponse = callLlm(requestType, lookupTerm, systemPrompt, jsonOnlyPrompt);
    String retryAttempt = retryResponse.firstChoice();

    result = tryParseJson(retryAttempt);
    if (result != null) {
      return result; // Success on retry
    }

    // Both attempts failed — give up and return empty
    log.error(
      "LLM response still invalid JSON after retry (provider={}, requestType={}), returning empty result",
      getProvider(),
      requestType
    );
    return new QuickFactsResult();
  }

  /**
   * Attempts to parse JSON response. Returns null if parsing fails (caller can retry).
   */
  private QuickFactsResult tryParseJson(String json) {
    try {
      log.debug("QuickFacts raw LLM response (provider={}): {}", getProvider(), json);
      QuickFactsResult result = MAPPER.readValue(sanitizeRawJson(json), QuickFactsResult.class);
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

  private ChatResponse callLlm(
    RequestType requestType,
    String lookupTerm,
    String systemPrompt,
    String userPrompt
  ) {
    ChatRequest request = buildRequest(systemPrompt, userPrompt);

    // Estimate token count before calling LLM (for TPM monitoring)
    int estimatedInputTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
    log.debug(
      "[LLM] Estimated request tokens: {} (system: {}, user: {})",
      estimatedInputTokens,
      estimateTokens(systemPrompt),
      estimateTokens(userPrompt)
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
      // Log actual vs estimated tokens for TPM monitoring
      int actualInputTokens = response.getTokensInput();
      int actualOutputTokens = response.getTokensOutput();
      log.debug(
        "[LLM] Actual tokens — Input: {} (estimate: {}), Output: {}, Duration: {}ms",
        actualInputTokens,
        estimatedInputTokens,
        actualOutputTokens,
        duration
      );
      usageLogService.log(
        getProvider(),
        requestType,
        lookupTerm,
        200,
        response.getTokensInput(),
        response.getTokensOutput(),
        duration
      );
      return response;
    } catch (HttpClientErrorException e) {
      long duration = System.currentTimeMillis() - start;
      int status = e.getStatusCode().value();
      usageLogService.log(getProvider(), requestType, lookupTerm, status, null, null, duration);
      if (status == 429) {
        var headers = e.getResponseHeaders();
        String retryAfterHeader = headers != null ? headers.getFirst("Retry-After") : null;
        int retryAfterSeconds = RateLimitException.parseRetryAfter(retryAfterHeader);
        log.warn(
          "LLM rate limited (provider={}, retryAfter={}s)",
          getProvider(),
          retryAfterSeconds
        );
        throw new RateLimitException(retryAfterSeconds, getProvider(), getModel());
      }
      log.warn(
        "LLM call failed (provider={}, key={}, status={}): {}",
        getProvider(),
        getApiKeyInfo(),
        status,
        e.getMessage()
      );
      throw e;
    } catch (RuntimeException e) {
      long duration = System.currentTimeMillis() - start;
      log.warn(
        "LLM call failed (provider={}, key={}): {}",
        getProvider(),
        getApiKeyInfo(),
        e.getMessage()
      );
      usageLogService.log(getProvider(), requestType, lookupTerm, 500, null, null, duration);
      throw e;
    }
  }

  private ChatRequest buildRequest(String systemPrompt, String userPrompt) {
    List<ChatRequest.Message> messages = new ArrayList<>();
    if (systemPrompt != null && !systemPrompt.isBlank()) {
      messages.add(new ChatRequest.Message("system", systemPrompt));
    }
    messages.add(new ChatRequest.Message("user", userPrompt));
    // adjust the temperature if required
    return new ChatRequest(getModel(), messages, 0.0, 256);
  }

  protected QuickFactsResult parseJson(String json) {
    try {
      log.debug("QuickFacts raw LLM response (provider={}): {}", getProvider(), json);
      QuickFactsResult result = MAPPER.readValue(sanitizeRawJson(json), QuickFactsResult.class);
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

  /**
   * Fixes a common LLM output error: inch marks written as ASCII double-quotes inside
   * JSON string values (e.g. {@code "24""} instead of {@code "24 Zoll"}).
   * Replaces {@code digit"} followed by a JSON structural character (comma, whitespace,
   * closing brace/bracket) with {@code digit Zoll"}.
   */
  private static String sanitizeRawJson(String json) {
    if (json == null) return null;
    return json.replaceAll("(\\d)\"([,\\s}\\]])", "$1 Zoll\"$2");
  }

  private static String truncate(String text, int maxChars) {
    if (text == null) return "";
    return text.length() <= maxChars ? text : text.substring(0, maxChars);
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
