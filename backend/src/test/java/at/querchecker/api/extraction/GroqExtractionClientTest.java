package at.querchecker.api.extraction;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.model.ChatResponse;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.deepLearning.entity.DlCategoryPrompt;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroqExtractionClientTest {

    @Mock RestClient restClient;
    @Mock ApiUsageLogService usageLogService;
    @InjectMocks GroqExtractionClient client;

    // RestClient fluent-chain mocks
    RestClient.RequestBodyUriSpec uriSpec;
    RestClient.RequestBodySpec bodySpec;
    RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        // RETURNS_SELF: all fluent methods returning RequestBodySpec auto-return bodySpec
        bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        responseSpec = mock(RestClient.ResponseSpec.class);

        lenient().when(restClient.post()).thenReturn(uriSpec);
        lenient().when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        lenient().when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void extractProductName_replacesAllPlaceholders() {
        givenGroqReturns("ThinkPad X1 Carbon Gen 12");
        DlCategoryPrompt prompt = prompt(
                "Du extrahierst Produktnamen.",
                "Kategorie: {category}\nTitel: {title}\n\n{description}");

        client.extractProductName("ThinkPad Laptop", "sehr gepflegt", "Laptop / Notebook", prompt);

        verify(restClient).post();
    }

    @Test
    void extractProductName_truncatesLongDescription() {
        givenGroqReturns("HP LaserJet");
        DlCategoryPrompt prompt = prompt("system", "{description}");
        String longDesc = "x".repeat(2000);

        assertThatNoException().isThrownBy(() ->
                client.extractProductName("HP", longDesc, "Drucker", prompt));
    }

    @Test
    void extractQuickFacts_icecatIdNull_whenNotInBraveUrls() {
        givenGroqReturns("""
                {
                  "quickFacts": { "cpu": "Core i7" },
                  "sources": { "icecatId": "99999", "sourceUrl": "https://icecat.biz/p/fake-99999.html" }
                }
                """);
        List<SearchResult> results = List.of(
                searchResult("https://icecat.biz/p/lenovo-12345.html"));

        QuickFactsResult result = client.extractQuickFacts(
                "ThinkPad", "Laptop", results, List.of(), prompt("s", "u"));

        assertThat(result.getSources().getIcecatId()).isNull();
    }

    @Test
    void extractQuickFacts_icecatIdValid_whenFoundInBraveUrls() {
        givenGroqReturns("""
                {
                  "quickFacts": { "cpu": "Core Ultra 7" },
                  "sources": { "icecatId": "12345", "sourceUrl": "https://icecat.biz/p/lenovo-12345.html" }
                }
                """);
        List<SearchResult> results = List.of(
                searchResult("https://icecat.biz/p/lenovo-12345.html", "Lenovo ThinkPad specs"));

        QuickFactsResult result = client.extractQuickFacts(
                "ThinkPad", "Laptop", results, List.of(), prompt("s", "u"));

        assertThat(result.getSources().getIcecatId()).isEqualTo("12345");
    }

    @Test
    void extractQuickFacts_logsTokenUsage() {
        givenGroqReturns("{\"quickFacts\": {}, \"sources\": {}}", 950, 150);

        client.extractQuickFacts("ThinkPad", "Laptop", List.of(), List.of(), prompt("s", "u"));

        verify(usageLogService).log(eq(Provider.GROQ), eq(RequestType.EXTRACTION),
                any(), anyInt(), eq(950), eq(150), anyLong());
    }

    @Test
    void extractQuickFacts_handlesInvalidJson_withoutException() {
        givenGroqReturns("kein JSON");
        assertThatNoException().isThrownBy(() ->
                client.extractQuickFacts("test", "cat", List.of(), List.of(), prompt("s", "u")));
    }

    @Test
    void extractQuickFactsFromText_callsLlmWithPageText() {
        givenGroqReturns("{\"quickFacts\": {\"panel\": \"IPS\"}, \"sources\": {}}");

        QuickFactsResult result = client.extractQuickFactsFromText(
                "Samsung S95D", "Fernseher", "Panel: IPS\nSize: 65\"",
                List.of("panel", "size"), prompt("s", "{snippets}"));

        assertThat(result.getQuickFacts()).containsKey("panel");
    }

    // --- Hilfsmethoden ---

    private void givenGroqReturns(String content) {
        givenGroqReturns(content, 500, 100);
    }

    private void givenGroqReturns(String content, int tokensIn, int tokensOut) {
        ChatResponse response = buildChatResponse(content, tokensIn, tokensOut);
        lenient().when(responseSpec.body(ArgumentMatchers.<Class<ChatResponse>>eq(ChatResponse.class)))
                .thenReturn(response);
    }

    private ChatResponse buildChatResponse(String content, int tokensIn, int tokensOut) {
        ChatResponse response = new ChatResponse();

        ChatResponse.Choice.Message message = new ChatResponse.Choice.Message();
        message.setContent(content);

        ChatResponse.Choice choice = new ChatResponse.Choice();
        choice.setMessage(message);

        List<ChatResponse.Choice> choices = new ArrayList<>();
        choices.add(choice);
        response.setChoices(choices);

        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(tokensIn);
        usage.setCompletionTokens(tokensOut);
        response.setUsage(usage);

        return response;
    }

    private SearchResult searchResult(String url) {
        return searchResult(url, "Test Product");
    }

    private SearchResult searchResult(String url, String title) {
        return SearchResult.builder()
                .title(title)
                .url(url)
                .description("Test description")
                .extraSnippets(List.of())
                .build();
    }

    private DlCategoryPrompt prompt(String system, String user) {
        return DlCategoryPrompt.builder()
                .systemPrompt(system)
                .userPrompt(user)
                .build();
    }
}
