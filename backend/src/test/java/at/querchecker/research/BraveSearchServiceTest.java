package at.querchecker.research;

import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.service.ApiUsageLogService;
import at.querchecker.research.model.BraveApiResponse;
import at.querchecker.research.model.BraveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BraveSearchServiceTest {

    @Mock RestTemplate restTemplate;
    @Mock ApiUsageLogService usageLogService;
    @Mock ProviderProperties providerProperties;
    @InjectMocks BraveSearchService service;

    @BeforeEach
    void setUp() {
        ProviderConfig config = new ProviderConfig();
        config.setApiKey("test-key");
        lenient().when(providerProperties.getProvider(Provider.BRAVE)).thenReturn(config);
    }

    @Test
    void search_returnsResults_onFirstStageSucess() {
        givenBraveReturns(stageOneUrl(), List.of(braveResult("Lenovo Yoga 7", "https://icecat.biz/p/lenovo-yoga-7-12345678.html")));

        List<BraveResult> results = service.search("Lenovo Yoga 7", List.of("cpu", "ram"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Lenovo Yoga 7");
        // Nur 1 Brave-Call — Stufe 2 und 3 nicht ausgeführt
        verify(restTemplate, times(1)).exchange(anyString(), any(HttpMethod.class), any(),
                ArgumentMatchers.<Class<BraveApiResponse>>eq(BraveApiResponse.class));
    }

    @Test
    void search_fallsToStageTwo_whenStageOneEmpty() {
        givenBraveReturns(stageOneUrl(), List.of());
        givenBraveReturns(stageTwoUrl(), List.of(braveResult("Lenovo Yoga", "https://icecat.biz/p/lenovo-12345678.html")));

        List<BraveResult> results = service.search("Lenovo Yoga 7", List.of("cpu"));

        assertThat(results).hasSize(1);
        verify(restTemplate, times(2)).exchange(anyString(), any(HttpMethod.class), any(),
                ArgumentMatchers.<Class<BraveApiResponse>>eq(BraveApiResponse.class));
    }

    @Test
    void search_fallsToStageThree_whenStageOneAndTwoEmpty() {
        givenBraveReturns(stageOneUrl(), List.of());
        givenBraveReturns(stageTwoUrl(), List.of());
        givenBraveReturns(stageThreeUrl(), List.of(braveResult("Lenovo", "https://icecat.biz/p/12345678.html")));

        List<BraveResult> results = service.search("Lenovo Yoga 7", List.of());

        assertThat(results).hasSize(1);
        verify(restTemplate, times(3)).exchange(anyString(), any(HttpMethod.class), any(),
                ArgumentMatchers.<Class<BraveApiResponse>>eq(BraveApiResponse.class));
    }

    @Test
    void search_returnsEmpty_whenAllStagesEmpty() {
        givenBraveReturns(stageOneUrl(), List.of());
        givenBraveReturns(stageTwoUrl(), List.of());
        givenBraveReturns(stageThreeUrl(), List.of());

        assertThat(service.search("Unbekanntes Gerät", List.of())).isEmpty();
    }

    @Test
    void search_logsUsage_afterEachCall() {
        givenBraveReturns(stageOneUrl(), List.of());
        givenBraveReturns(stageTwoUrl(), List.of());
        givenBraveReturns(stageThreeUrl(), List.of());

        service.search("test", List.of());

        // 3 Stufen → 3 Log-Einträge
        verify(usageLogService, times(3)).log(eq(Provider.BRAVE), eq(RequestType.SEARCH),
                any(), anyInt(), isNull(), isNull(), anyLong());
    }

    @Test
    void search_queryContainsSiteIcecat() {
        // Alle Stufen müssen site:icecat.biz enthalten
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        givenBraveReturns(null, List.of());
        service.search("ThinkPad X1", List.of());
        verify(restTemplate, atLeastOnce()).exchange(
                urlCaptor.capture(), any(HttpMethod.class), any(), ArgumentMatchers.<Class<BraveApiResponse>>any());
        assertThat(urlCaptor.getAllValues())
                .allMatch(url -> url.contains("site%3Aicecat.biz") || url.contains("site:icecat.biz"));
    }

    // --- Hilfsmethoden ---

    /**
     * Richtet einen Stub für URLs ein, die dem Stufen-Muster entsprechen.
     * null → jede URL (verwendet in search_queryContainsSiteIcecat)
     * stageOneUrl() → "Spezifikationen" (ohne "technische")
     * stageTwoUrl() → "technische" (einzigartig für Stufe 2)
     * stageThreeUrl() → keine "Spezifikationen" in der URL
     */
    private void givenBraveReturns(String urlPattern, List<BraveResult> results) {
        ResponseEntity<BraveApiResponse> response = ResponseEntity.ok(toApiResponse(results));
        Predicate<String> urlMatcher = buildUrlMatcher(urlPattern);
        lenient().when(restTemplate.exchange(
                argThat((String url) -> url != null && urlMatcher.test(url)),
                eq(HttpMethod.GET), any(),
                ArgumentMatchers.<Class<BraveApiResponse>>eq(BraveApiResponse.class))
        ).thenReturn(response);
    }

    private Predicate<String> buildUrlMatcher(String urlPattern) {
        if (urlPattern == null) {
            return url -> true;
        }
        // stageThreeUrl() sentinel: match URLs WITHOUT "Spezifikationen"
        if (urlPattern.equals("__stage3__")) {
            return url -> !url.contains("Spezifikationen");
        }
        return url -> url.contains(urlPattern);
    }

    private BraveResult braveResult(String title, String url) {
        return BraveResult.builder()
                .title(title)
                .url(url)
                .description("Test description")
                .extraSnippets(List.of())
                .build();
    }

    /** Stufe 1: enthält "Spezifikationen" (aber nicht "technische") */
    private String stageOneUrl() { return "Spezifikationen"; }

    /** Stufe 2: enthält "technische" (in "technische Daten") — einzigartig für Stufe 2 */
    private String stageTwoUrl() { return "technische"; }

    /** Stufe 3: keine "Spezifikationen" in der URL */
    private String stageThreeUrl() { return "__stage3__"; }

    private BraveApiResponse toApiResponse(List<BraveResult> results) {
        BraveApiResponse response = new BraveApiResponse();
        BraveApiResponse.WebSection web = new BraveApiResponse.WebSection();
        List<BraveApiResponse.WebResult> webResults = new ArrayList<>();
        for (BraveResult r : results) {
            BraveApiResponse.WebResult wr = new BraveApiResponse.WebResult();
            wr.setTitle(r.getTitle());
            wr.setUrl(r.getUrl());
            wr.setDescription(r.getDescription());
            wr.setExtraSnippets(r.getExtraSnippets() != null ? r.getExtraSnippets() : List.of());
            webResults.add(wr);
        }
        web.setResults(webResults);
        response.setWeb(web);
        return response;
    }
}
