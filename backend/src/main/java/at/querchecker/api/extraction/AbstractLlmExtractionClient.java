package at.querchecker.api.extraction;

import at.querchecker.api.entity.RequestType;
import at.querchecker.api.model.ChatRequest;
import at.querchecker.api.model.ChatResponse;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.research.model.BraveResult;
import at.querchecker.research.model.QuickFactsResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
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
                                              List<BraveResult> braveResults,
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
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("LLM call failed (provider={}): {}", getProvider(), e.getMessage());
            usageLogService.log(getProvider(), requestType, lookupTerm, 500, null, null, duration);
            return new ChatResponse();
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

    private QuickFactsResult parseJson(String json) {
        try {
            log.debug("QuickFacts raw LLM response (provider={}): {}", getProvider(), json);
            QuickFactsResult result = MAPPER.readValue(json, QuickFactsResult.class);
            if (result.getSources() == null) {
                result.setSources(new QuickFactsResult.Sources());
            }
            return result;
        } catch (Exception e) {
            log.warn("parseJson failed (provider={}): {}", getProvider(), e.getMessage());
            return new QuickFactsResult();
        }
    }

    private QuickFactsResult applyIcecatIdSafetyCheck(QuickFactsResult result,
                                                       List<BraveResult> braveResults,
                                                       String lookupTerm) {
        if (result.getSources() == null) return result;
        String icecatId = result.getSources().getIcecatId();
        if (icecatId == null) return result;
        String icecatIdLower = icecatId.toLowerCase();

        // Step 1: ID must appear in at least one Brave URL
        List<BraveResult> matchingResults = braveResults.stream()
                .filter(r -> r.getUrl() != null && r.getUrl().toLowerCase().contains(icecatIdLower))
                .toList();
        if (matchingResults.isEmpty()) {
            log.debug("Safety check: icecatId='{}' not found in Brave URLs — discarding", icecatId);
            result.getSources().setIcecatId(null);
            result.getSources().setIcecatUrl(null);
            return result;
        }

        // Step 2: The Brave result(s) containing this ID must mention the lookup term's brand
        // (first word, e.g. "Microsoft" from "Microsoft Surface Laptop 5").
        // This prevents picking up unrelated cross-linked products from Icecat sidebars.
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
                    result.getSources().setIcecatUrl(null);
                }
            }
        }
        return result;
    }

    private String formatSnippets(List<BraveResult> results) {
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
