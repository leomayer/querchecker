package at.querchecker.api.extraction;

import at.querchecker.api.entity.RequestType;
import at.querchecker.api.model.ChatRequest;
import at.querchecker.api.model.ChatResponse;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.model.SearchResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gemeinsame Logik für LLM-basierte ExtractionClient-Implementierungen
 * (Groq, OpenRouter — beide nutzen OpenAI-kompatibles API-Format).
 */
@Slf4j
public abstract class AbstractLlmExtractionClient implements ExtractionClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    protected final RestClient restClient;
    protected final ApiUsageLogService usageLogService;

    protected AbstractLlmExtractionClient(RestClient restClient, ApiUsageLogService usageLogService) {
        this.restClient = restClient;
        this.usageLogService = usageLogService;
    }

    /** Vollständiger Endpunkt-URL für den jeweiligen Provider */
    protected abstract String getEndpointUrl();

    /** Modellname aus der Provider-Konfiguration */
    protected abstract String getModel();

    @Override
    public String extractProductName(String title, String description,
                                     String categoryName, DlCategoryPrompt prompt) {
        String userPrompt = prompt.getUserPrompt()
                .replace("{title}", title)
                .replace("{description}", truncate(description, 800))
                .replace("{category}", categoryName);

        ChatResponse response = callLlm(RequestType.EXTRACTION, null,
                prompt.getSystemPrompt(), userPrompt);
        return response.firstChoice().trim();
    }

    @Override
    public QuickFactsResult extractQuickFacts(String lookupTerm, String categoryName,
                                              List<SearchResult> braveResults,
                                              List<String> mandatoryFields,
                                              DlCategoryPrompt prompt) {
        String snippetsBlock = formatSnippets(braveResults);
        String userPrompt = prompt.getUserPrompt()
                .replace("{lookupTerm}", lookupTerm)
                .replace("{category}", categoryName)
                .replace("{snippets}", snippetsBlock)
                .replace("{mandatoryFields}", String.join(", ", mandatoryFields));

        ChatResponse response = callLlm(RequestType.EXTRACTION, lookupTerm,
                prompt.getSystemPrompt(), userPrompt);

        QuickFactsResult result = parseJson(response.firstChoice());
        return applyIcecatIdSafetyCheck(result, braveResults, lookupTerm);
    }

    @Override
    public QuickFactsResult extractQuickFactsFromText(String lookupTerm, String categoryName,
                                                      String pageText,
                                                      List<String> mandatoryFields,
                                                      DlCategoryPrompt prompt) {
        // Identisch zu extractQuickFacts(), aber {snippets} wird mit pageText befüllt
        String userPrompt = prompt.getUserPrompt()
                .replace("{lookupTerm}", lookupTerm)
                .replace("{category}", categoryName)
                .replace("{snippets}", pageText)
                .replace("{mandatoryFields}", String.join(", ", mandatoryFields));

        ChatResponse response = callLlm(RequestType.EXTRACTION, lookupTerm,
                prompt.getSystemPrompt(), userPrompt);

        return parseJson(response.firstChoice());
    }

    // --- private helpers ---

    private ChatResponse callLlm(RequestType requestType, String lookupTerm,
                                  String systemPrompt, String userPrompt) {
        ChatRequest request = buildRequest(systemPrompt, userPrompt);

        long start = System.currentTimeMillis();
        try {
            ChatResponse response = restClient.post()
                    .uri(getEndpointUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
            long duration = System.currentTimeMillis() - start;

            if (response == null) response = new ChatResponse();
            usageLogService.log(getProvider(), requestType, lookupTerm, 200,
                    response.getTokensInput(), response.getTokensOutput(), duration);
            return response;
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("LLM call failed (provider={}): {}", getProvider(), e.getMessage());
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
        return new ChatRequest(getModel(), messages);
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
            log.warn("parseJson failed (provider={}, error={}, rawResponse={})", getProvider(), e.getMessage(), json);
            return new QuickFactsResult();
        }
    }

    private QuickFactsResult applyIcecatIdSafetyCheck(QuickFactsResult result,
                                                       List<SearchResult> braveResults,
                                                       String lookupTerm) {
        if (result.getSources() == null) return result;
        String icecatId = result.getSources().getIcecatId();
        if (icecatId == null) return result;
        String icecatIdLower = icecatId.toLowerCase();

        // Step 1: ID must appear in at least one Brave URL that is an actual product page.
        // Asset/file URLs (objects.icecat.biz, PDFs, images) are rejected — they embed
        // file-object IDs that look like product IDs but are not.
        List<SearchResult> matchingResults = braveResults.stream()
                .filter(r -> r.getUrl() != null
                        && r.getUrl().toLowerCase().contains(icecatIdLower)
                        && isProductPageUrl(r.getUrl()))
                .toList();
        if (matchingResults.isEmpty()) {
            log.debug("Safety check: icecatId='{}' not found in Brave product-page URLs — discarding", icecatId);
            result.getSources().setIcecatId(null);
            result.getSources().setSourceUrl(null);
            return result;
        }

        // Step 1b: Correct the icecatId to the actual product ID extracted from the URL.
        matchingResults.stream()
                .map(r -> extractIcecatProductId(r.getUrl()))
                .filter(id -> id != null && !id.equals(icecatId))
                .findFirst()
                .ifPresent(corrected -> {
                    log.debug("Safety check: correcting icecatId '{}' → '{}' (from URL slug)", icecatId, corrected);
                    result.getSources().setIcecatId(corrected);
                });

        // Step 2: Brand check — prevents picking up cross-linked products from Icecat sidebars.
        if (lookupTerm != null && !lookupTerm.isBlank()) {
            String brand = lookupTerm.trim().split("\\s+")[0].toLowerCase();
            if (brand.length() > 2) {
                boolean brandMatch = matchingResults.stream().anyMatch(r -> {
                    String title = r.getTitle() != null ? r.getTitle().toLowerCase() : "";
                    String desc = r.getDescription() != null ? r.getDescription().toLowerCase() : "";
                    return title.contains(brand) || desc.contains(brand);
                });
                if (!brandMatch) {
                    log.debug("Safety check: icecatId='{}' found in Brave URL but result doesn't mention brand '{}' — discarding",
                            icecatId, brand);
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
        if (lower.endsWith(".pdf") || lower.endsWith(".jpg") || lower.endsWith(".png")
                || lower.endsWith(".jpeg") || lower.endsWith(".webp")) return false;
        return lower.contains("icecat.biz");
    }

    private String formatSnippets(List<SearchResult> results) {
        return results.stream().map(r ->
                "---\nURL: " + r.getUrl() + "\nTitel: " + r.getTitle()
                + "\nSnippet: " + r.getDescription()
                + (r.getExtraSnippets() == null || r.getExtraSnippets().isEmpty() ? ""
                        : "\nExtra: " + String.join(" | ", r.getExtraSnippets()))
        ).collect(Collectors.joining("\n"));
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}
