package at.querchecker.research;

import static at.querchecker.research.entity.SourceType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import at.querchecker.api.extraction.ExtractionClient;
import at.querchecker.api.extraction.ExtractionProviderRouter;
import at.querchecker.api.result.ApiCallResult;
import at.querchecker.api.search.SearchProperties;
import at.querchecker.api.search.WebSearchProviderRouter;
import at.querchecker.api.search.WebSearchService;
import at.querchecker.api.service.QuotaService;
import at.querchecker.api.service.QuotaStatus;
import at.querchecker.deepLearning.service.DlPromptResolver;
import at.querchecker.entity.WhCategory;
import at.querchecker.repository.WhItemRepository;
import at.querchecker.research.entity.CategorySearchSource;
import at.querchecker.research.entity.ExtractionQuality;
import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.ProductLookup;
import at.querchecker.research.entity.SourceType;
import at.querchecker.research.model.ProductLookupResult;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.model.SearchResult;
import at.querchecker.research.repository.ProductLookupRepository;
import at.querchecker.service.AppConfigService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductLookupServiceTest {

  @Mock
  ProductLookupRepository repo;

  @Mock
  QuotaService quotaService;

  @Mock
  SearchProperties searchProperties;

  @Mock
  WebSearchProviderRouter webSearchRouter;

  @Mock
  WebSearchService webSearchService;

  @Mock
  ExtractionQualityEvaluator qualityEvaluator;

  @Mock
  UrlValidator urlValidator;

  @Mock
  CategorySearchSourceService sourceService;

  @Mock
  CategorySpecPreferenceService prefService;

  @Mock
  ExtractionClient llmClient;

  @Mock
  ExtractionProviderRouter extractionRouter;

  @Mock
  DlPromptResolver promptResolver;

  @Mock
  AppConfigService appConfigService;

  @Mock
  SearchResultCacheService searchResultCacheService;

  @Mock
  at.querchecker.sse.SseHub sseHub;

  @Mock
  WhItemRepository whItemRepository;

  @InjectMocks
  ProductLookupService service;

  @BeforeEach
  void routerSetup() {
    lenient().when(extractionRouter.getActive()).thenReturn(llmClient);
    lenient().when(webSearchRouter.getActive()).thenReturn(webSearchService);
    lenient().when(appConfigService.getLookupFailedTtlHours()).thenReturn(24);
    lenient().when(appConfigService.getLookupErrorTtlMinutes()).thenReturn(10);
    lenient().doCallRealMethod().when(urlValidator).normalizeUrl(any());
  }

  // --- Snippets-Pfad für alle Quellen ---

  @Test
  void lookup_gsmarenaSource_usesSnippetsPath_notHtmlFetch() {
    setupNoCache();
    setupQuotaOk();
    CategorySearchSource src = source("gsmarena.com", GSMARENA);
    when(sourceService.findForCategory(any())).thenReturn(List.of(src));

    String url = "https://www.gsmarena.com/samsung_galaxy_s25-13322.php";
    when(webSearchService.search(any(), any(), any(), any(), anyInt())).thenReturn(
      new ApiCallResult.Success<>(List.of(searchResult(url)))
    );
    when(llmClient.extractQuickFacts(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(
      quickFacts(Map.of("cpu", "Snapdragon 8 Elite"), null, url)
    );
    when(urlValidator.resolveIcecatId(any(), any())).thenReturn(null);
    when(urlValidator.resolveSourceUrl(any(), any())).thenReturn(url);
    when(urlValidator.matchesExpectedPattern(url, GSMARENA)).thenReturn(true);
    when(qualityEvaluator.evaluate(any(), any(), eq(GSMARENA))).thenReturn(ExtractionQuality.GOOD);

    ProductLookupResult result = service.lookup(
      null,
      "Samsung Galaxy S25",
      mock(WhCategory.class),
      null
    );

    assertThat(result.getStatus()).isEqualTo(LookupStatus.COMPLETE);
    verify(llmClient).extractQuickFacts(any(), any(), any(), any(), any(), any(), anyInt());
    verify(llmClient, never()).extractQuickFactsFromText(any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  void lookup_flatpanelshdSource_usesSnippetsPath_notHtmlFetch() {
    setupNoCache();
    setupQuotaOk();
    CategorySearchSource src = source("flatpanelshd.com", FLATPANELSHD);
    when(sourceService.findForCategory(any())).thenReturn(List.of(src));

    String url = "https://www.flatpanelshd.com/lg_c4_oled_2024.php";
    when(webSearchService.search(any(), any(), any(), any(), anyInt())).thenReturn(
      new ApiCallResult.Success<>(List.of(searchResult(url)))
    );
    when(llmClient.extractQuickFacts(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(
      quickFacts(Map.of("screen_size", "65\""), null, url)
    );
    when(urlValidator.resolveIcecatId(any(), any())).thenReturn(null);
    when(urlValidator.resolveSourceUrl(any(), any())).thenReturn(url);
    when(urlValidator.matchesExpectedPattern(url, FLATPANELSHD)).thenReturn(true);
    when(qualityEvaluator.evaluate(any(), any(), eq(FLATPANELSHD))).thenReturn(
      ExtractionQuality.GOOD
    );

    ProductLookupResult result = service.lookup(null, "LG C4 OLED", mock(WhCategory.class), null);

    assertThat(result.getStatus()).isEqualTo(LookupStatus.COMPLETE);
    verify(llmClient).extractQuickFacts(any(), any(), any(), any(), any(), any(), anyInt());
    verify(llmClient, never()).extractQuickFactsFromText(any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  void lookup_sourceUrl_comesFromUrlValidator() {
    // sourceUrl comes from urlValidator (LLM return value is an input to urlValidator, not used directly)
    setupNoCache();
    setupQuotaOk();
    CategorySearchSource src = source("gsmarena.com", GSMARENA);
    when(sourceService.findForCategory(any())).thenReturn(List.of(src));

    String validatedUrl = "https://www.gsmarena.com/samsung_galaxy_s25-13322.php";
    when(webSearchService.search(any(), any(), any(), any(), anyInt())).thenReturn(
      new ApiCallResult.Success<>(List.of(searchResult(validatedUrl)))
    );
    when(llmClient.extractQuickFacts(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(
      quickFacts(Map.of("cpu", "Snapdragon 8 Elite"), null, "https://halluziniert.com/falsche-url")
    );
    when(urlValidator.resolveIcecatId(any(), any())).thenReturn(null);
    when(urlValidator.resolveSourceUrl(any(), any())).thenReturn(validatedUrl);
    when(urlValidator.matchesExpectedPattern(validatedUrl, GSMARENA)).thenReturn(true);
    when(qualityEvaluator.evaluate(any(), any(), any())).thenReturn(ExtractionQuality.GOOD);

    ProductLookupResult result = service.lookup(
      null,
      "Samsung Galaxy S25",
      mock(WhCategory.class),
      null
    );

    assertThat(result.getSourceUrl()).isEqualTo(validatedUrl);
    assertThat(result.getSourceUrl()).doesNotContain("halluziniert.com");
  }

  @Test
  void lookup_cascadesToNextSource_whenFirstSourceReturnsEmpty() {
    setupNoCache();
    setupQuotaOk();
    CategorySearchSource flatpanelsSrc = source("flatpanelshd.com", FLATPANELSHD);
    CategorySearchSource genericSrc = source("whathifi.com", GENERIC);
    when(sourceService.findForCategory(any())).thenReturn(List.of(flatpanelsSrc, genericSrc));

    when(webSearchService.search(any(), eq("flatpanelshd.com"), any(), any(), anyInt())).thenReturn(
      new ApiCallResult.Success<>(List.of(searchResult("https://flatpanelshd.com/lg_g5.php")))
    );
    when(webSearchService.search(any(), eq("whathifi.com"), any(), any(), anyInt())).thenReturn(
      new ApiCallResult.Success<>(List.of(searchResult("https://whathifi.com/lg-g5-review")))
    );

    when(llmClient.extractQuickFacts(any(), any(), any(), any(), any(), any(), anyInt()))
      .thenReturn(quickFacts(Map.of(), null, null))
      .thenReturn(quickFacts(Map.of("screen_size", "65\""), null, null));
    when(urlValidator.resolveIcecatId(any(), any())).thenReturn(null);
    when(urlValidator.resolveSourceUrl(any(), any())).thenReturn(null);
    when(urlValidator.matchesExpectedPattern(isNull(), any())).thenReturn(false);
    when(qualityEvaluator.evaluate(any(), any(), eq(FLATPANELSHD))).thenReturn(
      ExtractionQuality.EMPTY
    );
    when(qualityEvaluator.evaluate(any(), any(), eq(GENERIC))).thenReturn(ExtractionQuality.GOOD);

    ProductLookupResult result = service.lookup(null, "LG G5", mock(WhCategory.class), null);

    assertThat(result.getStatus()).isEqualTo(LookupStatus.COMPLETE);
    assertThat(result.getSourceDomain()).isEqualTo("whathifi.com");
    verify(llmClient, times(2)).extractQuickFacts(any(), any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  void lookup_usesSearchResultCount_fromSource() {
    setupNoCache();
    setupQuotaOk();
    CategorySearchSource src = source("gsmarena.com", GSMARENA);
    src.setSearchResultCount(3);
    when(sourceService.findForCategory(any())).thenReturn(List.of(src));
    when(webSearchService.search(any(), any(), any(), any(), eq(3))).thenReturn(new ApiCallResult.Success<>(List.of()));

    service.lookup(null, "Samsung S25", mock(WhCategory.class), null);

    verify(webSearchService).search(any(), any(), any(), any(), eq(3));
  }

  // --- NO_SOURCES ---

  @Test
  void lookup_returnsNoSources_whenSourcesEmpty() {
    setupNoCache();
    setupQuotaOk();
    when(sourceService.findForCategory(any())).thenReturn(List.of());

    ProductLookupResult result = service.lookup(
      null,
      "Unbekanntes Gerät",
      mock(WhCategory.class),
      null
    );

    assertThat(result.getStatus()).isEqualTo(LookupStatus.NO_SOURCES);
    verify(repo, never()).save(any());
  }

  // --- ERROR: Exception → gecacht, TTL 10min ---

  @Test
  void lookup_savesError_andReturnsError_onException() {
    setupNoCache();
    setupQuotaOk();
    CategorySearchSource src = source("icecat.biz", ICECAT);
    when(sourceService.findForCategory(any())).thenReturn(List.of(src));
    when(webSearchService.search(any(), any(), any(), any(), anyInt())).thenThrow(
      new RuntimeException("Brave API nicht erreichbar")
    );

    ProductLookupResult result = service.lookup(null, "Samsung S25", mock(WhCategory.class), null);

    assertThat(result.getStatus()).isEqualTo(LookupStatus.ERROR);
    verify(repo).save(argThat((pl) -> pl.getLookupStatus() == LookupStatus.ERROR));
  }

  @Test
  void lookup_returnsErrorFromCache_whenTtlNotExpired() {
    ProductLookup cached = cachedResult(LookupStatus.ERROR, LocalDateTime.now().minusMinutes(5));
    when(repo.findByLookupTerm(any())).thenReturn(Optional.of(cached));
    when(appConfigService.getLookupErrorTtlMinutes()).thenReturn(10);

    ProductLookupResult result = service.lookup(null, "Samsung S25", mock(WhCategory.class), null);

    assertThat(result.getStatus()).isEqualTo(LookupStatus.ERROR);
    verify(quotaService, never()).checkQuota(any());
  }

  @Test
  void lookup_retriesAfterError_whenTtlExpired() {
    ProductLookup cached = cachedResult(LookupStatus.ERROR, LocalDateTime.now().minusMinutes(15));
    when(repo.findByLookupTerm(any())).thenReturn(Optional.of(cached));
    when(appConfigService.getLookupErrorTtlMinutes()).thenReturn(10);
    setupQuotaOk();
    when(sourceService.findForCategory(any())).thenReturn(List.of());

    ProductLookupResult result = service.lookup(null, "Samsung S25", mock(WhCategory.class), null);

    verify(sourceService).findForCategory(any());
    assertThat(result.getStatus()).isEqualTo(LookupStatus.NO_SOURCES);
  }

  // --- FAILED: TTL 24h ---

  @Test
  void lookup_returnsFailedFromCache_whenTtlNotExpired() {
    ProductLookup cached = cachedResult(LookupStatus.FAILED, LocalDateTime.now().minusHours(12));
    when(repo.findByLookupTerm(any())).thenReturn(Optional.of(cached));
    when(appConfigService.getLookupFailedTtlHours()).thenReturn(24);

    ProductLookupResult result = service.lookup(
      null,
      "Unbekanntes TV",
      mock(WhCategory.class),
      null
    );

    assertThat(result.getStatus()).isEqualTo(LookupStatus.FAILED);
    verify(quotaService, never()).checkQuota(any());
  }

  @Test
  void lookup_retriesAfterFailed_whenTtlExpired() {
    ProductLookup cached = cachedResult(LookupStatus.FAILED, LocalDateTime.now().minusHours(30));
    when(repo.findByLookupTerm(any())).thenReturn(Optional.of(cached));
    when(appConfigService.getLookupFailedTtlHours()).thenReturn(24);
    setupQuotaOk();
    when(sourceService.findForCategory(any())).thenReturn(List.of());

    ProductLookupResult result = service.lookup(
      null,
      "Unbekanntes TV",
      mock(WhCategory.class),
      null
    );

    verify(sourceService).findForCategory(any());
    assertThat(result.getStatus()).isEqualTo(LookupStatus.NO_SOURCES);
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

  private CategorySearchSource source(String domain, SourceType type) {
    return CategorySearchSource.builder()
      .siteDomain(domain)
      .sourceType(type)
      .lookupEnabled(true)
      .active(true)
      .searchResultCount(10)
      .build();
  }

  private SearchResult searchResult(String url) {
    return SearchResult.builder().url(url).extraSnippets(List.of()).build();
  }

  private ProductLookup cachedResult(LookupStatus status, LocalDateTime updatedAt) {
    ProductLookup pl = new ProductLookup();
    pl.setLookupStatus(status);
    pl.setUpdatedAt(updatedAt);
    return pl;
  }

  private QuickFactsResult quickFacts(
    Map<String, String> facts,
    String icecatId,
    String sourceUrl
  ) {
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
