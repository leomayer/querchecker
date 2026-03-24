package at.querchecker.research;

import at.querchecker.api.extraction.ExtractionClient;
import at.querchecker.api.extraction.ExtractionProviderRouter;
import at.querchecker.api.entity.Provider;
import at.querchecker.api.service.QuotaService;
import at.querchecker.api.service.QuotaStatus;
import at.querchecker.deepLearning.service.DlPromptResolver;
import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.CategorySearchSource;
import at.querchecker.research.entity.ExtractionQuality;
import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.ProductLookup;
import at.querchecker.research.entity.SourceType;
import at.querchecker.research.model.ProductLookupResult;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.model.SearchResult;
import at.querchecker.research.repository.ProductLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static at.querchecker.research.entity.SourceType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductLookupServiceTest {

    @Mock ProductLookupRepository repo;
    @Mock QuotaService quotaService;
    @Mock WebSearchService webSearchService;
    @Mock HtmlFetchService htmlFetchService;
    @Mock ExtractionQualityEvaluator qualityEvaluator;
    @Mock UrlValidator urlValidator;
    @Mock CategorySearchSourceService sourceService;
    @Mock CategorySpecPreferenceService prefService;
    @Mock ExtractionClient llmClient;
    @Mock ExtractionProviderRouter extractionRouter;
    @Mock DlPromptResolver promptResolver;
    @InjectMocks ProductLookupService service;

    @BeforeEach
    void routerSetup() {
        lenient().when(extractionRouter.getActive()).thenReturn(llmClient);
    }

    // --- HTML-Fetch Fallback-Loop ---

    @Test
    void lookup_htmlFetch_triesNextUrl_whenFirstFetchEmpty() {
        setupNoCache(); setupQuotaOk();
        CategorySearchSource src = htmlSource("flatpanelshd.com", FLATPANELSHD);
        when(sourceService.findForCategory(any())).thenReturn(List.of(src));
        when(prefService.getMandatoryFields(any())).thenReturn(List.of("screen_size"));
        when(prefService.getQueryKeywords(any())).thenReturn(List.of());

        List<SearchResult> brave = List.of(
            searchResult("https://www.flatpanelshd.com/lg_c4_oled_2024.php"),
            searchResult("https://www.flatpanelshd.com/lg_g5_oled_2025.php"));

        when(webSearchService.search(any(), any(), any(), any(), anyInt()))
            .thenReturn(brave);

        when(urlValidator.matchesExpectedPattern(anyString(), eq(FLATPANELSHD)))
            .thenReturn(true);

        // Erste URL → leer, zweite URL → Inhalt
        when(htmlFetchService.shouldFetchFullPage(FLATPANELSHD)).thenReturn(true);
        when(htmlFetchService.fetchAndExtract(
            "https://www.flatpanelshd.com/lg_c4_oled_2024.php", FLATPANELSHD))
            .thenReturn(Optional.empty());
        when(htmlFetchService.fetchAndExtract(
            "https://www.flatpanelshd.com/lg_g5_oled_2025.php", FLATPANELSHD))
            .thenReturn(Optional.of("<table>panel_type: OLED</table>"));

        QuickFactsResult extracted = quickFacts(
            Map.of("screen_size", "65\"", "panel_type", "OLED"), null,
            "https://www.flatpanelshd.com/lg_g5_oled_2025.php");
        when(llmClient.extractQuickFactsFromText(any(), any(), any(), any(), any()))
            .thenReturn(extracted);
        when(qualityEvaluator.evaluate(any(), any(), eq(FLATPANELSHD)))
            .thenReturn(ExtractionQuality.GOOD);

        ProductLookupResult result = service.lookup("LG G5", mock(WhCategory.class));

        assertThat(result.getStatus()).isEqualTo(LookupStatus.COMPLETE);
        assertThat(result.getSourceUrl())
            .isEqualTo("https://www.flatpanelshd.com/lg_g5_oled_2025.php");

        // Beide URLs wurden versucht
        verify(htmlFetchService).fetchAndExtract(
            "https://www.flatpanelshd.com/lg_c4_oled_2024.php", FLATPANELSHD);
        verify(htmlFetchService).fetchAndExtract(
            "https://www.flatpanelshd.com/lg_g5_oled_2025.php", FLATPANELSHD);
    }

    @Test
    void lookup_htmlFetch_skipsUrl_whenPatternMismatch() {
        setupNoCache(); setupQuotaOk();
        CategorySearchSource src = htmlSource("flatpanelshd.com", FLATPANELSHD);
        when(sourceService.findForCategory(any())).thenReturn(List.of(src));
        when(prefService.getMandatoryFields(any())).thenReturn(List.of("screen_size"));
        when(prefService.getQueryKeywords(any())).thenReturn(List.of());
        when(htmlFetchService.shouldFetchFullPage(FLATPANELSHD)).thenReturn(true);

        List<SearchResult> brave = List.of(
            searchResult("https://www.flatpanelshd.com/review.php?id=999"),
            searchResult("https://www.flatpanelshd.com/lg_g5_oled_2025.php"));

        when(webSearchService.search(any(), any(), any(), any(), anyInt()))
            .thenReturn(brave);
        when(urlValidator.matchesExpectedPattern(
            "https://www.flatpanelshd.com/review.php?id=999", FLATPANELSHD))
            .thenReturn(false);
        when(urlValidator.matchesExpectedPattern(
            "https://www.flatpanelshd.com/lg_g5_oled_2025.php", FLATPANELSHD))
            .thenReturn(true);
        when(htmlFetchService.fetchAndExtract(
            "https://www.flatpanelshd.com/lg_g5_oled_2025.php", FLATPANELSHD))
            .thenReturn(Optional.of("<table>screen_size: 65\"</table>"));

        when(llmClient.extractQuickFactsFromText(any(), any(), any(), any(), any()))
            .thenReturn(quickFacts(Map.of("screen_size", "65\""), null, null));
        when(qualityEvaluator.evaluate(any(), any(), any()))
            .thenReturn(ExtractionQuality.GOOD);

        service.lookup("LG G5", mock(WhCategory.class));

        // review.php wurde NICHT gefetcht
        verify(htmlFetchService, never()).fetchAndExtract(
            "https://www.flatpanelshd.com/review.php?id=999", FLATPANELSHD);
        verify(htmlFetchService).fetchAndExtract(
            "https://www.flatpanelshd.com/lg_g5_oled_2025.php", FLATPANELSHD);
    }

    @Test
    void lookup_htmlFetch_continuesNextSource_whenAllUrlsFail() {
        setupNoCache(); setupQuotaOk();
        CategorySearchSource htmlSrc    = htmlSource("flatpanelshd.com", FLATPANELSHD);
        CategorySearchSource snippetSrc = snippetSource("whathifi.com",  GENERIC);
        when(sourceService.findForCategory(any()))
            .thenReturn(List.of(htmlSrc, snippetSrc));
        when(prefService.getMandatoryFields(any())).thenReturn(List.of("screen_size"));
        when(prefService.getQueryKeywords(any())).thenReturn(List.of());
        when(htmlFetchService.shouldFetchFullPage(FLATPANELSHD)).thenReturn(true);
        when(htmlFetchService.shouldFetchFullPage(GENERIC)).thenReturn(false);

        // FlatpanelsHD: eine URL, Pattern ok, aber Fetch leer
        when(webSearchService.search(any(), eq("flatpanelshd.com"), any(), any(), anyInt()))
            .thenReturn(List.of(
                searchResult("https://www.flatpanelshd.com/lg_g5_oled_2025.php")));
        when(urlValidator.matchesExpectedPattern(anyString(), eq(FLATPANELSHD)))
            .thenReturn(true);
        when(htmlFetchService.fetchAndExtract(any(), eq(FLATPANELSHD)))
            .thenReturn(Optional.empty());

        // What Hi-Fi: Snippets-Pfad → liefert Ergebnis
        when(webSearchService.search(any(), eq("whathifi.com"), any(), any(), anyInt()))
            .thenReturn(List.of(searchResult("https://whathifi.com/lg-g5-review")));
        when(llmClient.extractQuickFacts(any(), any(), any(), any(), any()))
            .thenReturn(quickFacts(Map.of("screen_size", "65\""), null, null));
        when(qualityEvaluator.evaluate(any(), any(), eq(GENERIC)))
            .thenReturn(ExtractionQuality.GOOD);
        when(urlValidator.resolveSourceUrl(any(), any())).thenReturn(null);
        when(urlValidator.matchesExpectedPattern(isNull(), eq(GENERIC))).thenReturn(false);

        ProductLookupResult result = service.lookup("LG G5", mock(WhCategory.class));

        assertThat(result.getStatus()).isEqualTo(LookupStatus.COMPLETE);
        assertThat(result.getSourceDomain()).isEqualTo("whathifi.com");
    }

    @Test
    void lookup_htmlFetch_sourceUrlSetByJava_notLlm() {
        setupNoCache(); setupQuotaOk();
        CategorySearchSource src = htmlSource("gsmarena.com", GSMARENA);
        when(sourceService.findForCategory(any())).thenReturn(List.of(src));
        when(prefService.getMandatoryFields(any())).thenReturn(List.of());
        when(prefService.getQueryKeywords(any())).thenReturn(List.of());
        when(htmlFetchService.shouldFetchFullPage(GSMARENA)).thenReturn(true);

        String fetchedUrl = "https://www.gsmarena.com/samsung_galaxy_s25-13322.php";
        when(webSearchService.search(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(searchResult(fetchedUrl)));
        when(urlValidator.matchesExpectedPattern(fetchedUrl, GSMARENA)).thenReturn(true);
        when(htmlFetchService.fetchAndExtract(fetchedUrl, GSMARENA))
            .thenReturn(Optional.of("<table>cpu: Snapdragon 8 Elite</table>"));

        // LLM gibt eine andere sourceUrl zurück (sollte ignoriert werden)
        QuickFactsResult extracted = quickFacts(
            Map.of("cpu", "Snapdragon 8 Elite"), null,
            "https://halluziniert.com/falsche-url");
        when(llmClient.extractQuickFactsFromText(any(), any(), any(), any(), any()))
            .thenReturn(extracted);
        when(qualityEvaluator.evaluate(any(), any(), any()))
            .thenReturn(ExtractionQuality.GOOD);

        ProductLookupResult result =
            service.lookup("Samsung Galaxy S25", mock(WhCategory.class));

        // sourceUrl muss von Java kommen, nicht vom LLM
        assertThat(result.getSourceUrl()).isEqualTo(fetchedUrl);
        assertThat(result.getSourceUrl()).doesNotContain("halluziniert.com");
    }

    @Test
    void lookup_usesSearchResultCount_fromSource() {
        setupNoCache(); setupQuotaOk();
        CategorySearchSource src = htmlSource("gsmarena.com", GSMARENA);
        src.setSearchResultCount(3);
        when(sourceService.findForCategory(any())).thenReturn(List.of(src));
        when(prefService.getMandatoryFields(any())).thenReturn(List.of());
        when(prefService.getQueryKeywords(any())).thenReturn(List.of());
        when(webSearchService.search(any(), any(), any(), any(), eq(3)))
            .thenReturn(List.of());

        service.lookup("Samsung S25", mock(WhCategory.class));

        verify(webSearchService).search(any(), any(), any(), any(), eq(3));
    }

    // --- Hilfsmethoden ---

    private void setupNoCache() {
        when(repo.findByLookupTerm(any())).thenReturn(Optional.empty());
    }

    private void setupQuotaOk() {
        when(quotaService.checkQuota(any())).thenReturn(QuotaStatus.OK);
        lenient().when(prefService.getMandatoryFields(any())).thenReturn(List.of());
        lenient().when(prefService.getQueryKeywords(any())).thenReturn(List.of());
    }

    private CategorySearchSource htmlSource(String domain, SourceType type) {
        return CategorySearchSource.builder()
            .siteDomain(domain).sourceType(type)
            .lookupEnabled(true).active(true)
            .searchResultCount(3).build();
    }

    private CategorySearchSource snippetSource(String domain, SourceType type) {
        return CategorySearchSource.builder()
            .siteDomain(domain).sourceType(type)
            .lookupEnabled(true).active(true)
            .searchResultCount(10).build();
    }

    private SearchResult searchResult(String url) {
        return SearchResult.builder().url(url).extraSnippets(List.of()).build();
    }

    private QuickFactsResult quickFacts(Map<String, String> facts,
                                        String icecatId, String sourceUrl) {
        QuickFactsResult qfr = new QuickFactsResult();
        qfr.setQuickFacts(facts != null ? facts : Map.of());
        if (icecatId != null || sourceUrl != null) {
            QuickFactsResult.Sources sources = new QuickFactsResult.Sources();
            sources.setIcecatId(icecatId);
            sources.setSourceUrl(sourceUrl);
            qfr.setSources(sources);
        }
        return qfr;
    }
}
