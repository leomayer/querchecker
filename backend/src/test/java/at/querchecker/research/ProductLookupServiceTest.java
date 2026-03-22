package at.querchecker.research;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.service.QuotaService;
import at.querchecker.api.service.QuotaStatus;
import at.querchecker.entity.WhCategory;
import at.querchecker.research.entity.LookupStatus;
import at.querchecker.research.entity.ProductLookup;
import at.querchecker.research.model.BraveResult;
import at.querchecker.research.model.ProductLookupResult;
import at.querchecker.research.model.QuickFactsResult;
import at.querchecker.research.repository.ProductLookupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductLookupServiceTest {

    @Mock ProductLookupRepository repo;
    @Mock QuotaService quotaService;
    @Mock BraveSearchService braveSearchService;
    @Mock GroqExtractionService groqExtractionService;
    @Mock CategorySpecPreferenceService prefService;
    @InjectMocks ProductLookupService service;

    // --- Cache-Logik ---

    @Test
    void lookup_returnsCachedResult_whenComplete() {
        ProductLookup cached = lookup(LookupStatus.COMPLETE, "{\"cpu\":\"i7\"}");
        when(repo.findByLookupTerm("ThinkPad X1")).thenReturn(Optional.of(cached));

        ProductLookupResult result = service.lookup("ThinkPad X1", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.COMPLETE);
        assertThat(result.getQuickFactsJson()).isEqualTo("{\"cpu\":\"i7\"}");
        verifyNoInteractions(quotaService, braveSearchService, groqExtractionService);
    }

    @Test
    void lookup_returnsFailed_whenCachedFailed() {
        when(repo.findByLookupTerm("Unbekanntes Gerät"))
                .thenReturn(Optional.of(lookup(LookupStatus.FAILED, null)));

        ProductLookupResult result = service.lookup("Unbekanntes Gerät", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.FAILED);
        verifyNoInteractions(braveSearchService);
    }

    @Test
    void lookup_continuesAfterCachedQuotaExceeded_whenQuotaFree() {
        when(repo.findByLookupTerm("ThinkPad"))
                .thenReturn(Optional.of(lookup(LookupStatus.QUOTA_EXCEEDED, null)));
        when(quotaService.checkQuota(Provider.BRAVE)).thenReturn(QuotaStatus.OK);
        when(prefService.getQueryKeywords(any())).thenReturn(List.of());
        when(braveSearchService.search(any(), any())).thenReturn(List.of());
        lenient().when(prefService.getMandatoryFields(any())).thenReturn(List.of());

        service.lookup("ThinkPad", null);

        verify(braveSearchService).search(any(), any());
    }

    // --- Kontingent-Logik ---

    @Test
    void lookup_savesQuotaExceeded_whenQuotaFull() {
        when(repo.findByLookupTerm(any())).thenReturn(Optional.empty());
        when(quotaService.checkQuota(Provider.BRAVE)).thenReturn(QuotaStatus.QUOTA_EXCEEDED);

        ProductLookupResult result = service.lookup("ThinkPad X1", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.QUOTA_EXCEEDED);
        verify(repo).save(argThat(l -> l.getLookupStatus() == LookupStatus.QUOTA_EXCEEDED));
        verifyNoInteractions(braveSearchService);
    }

    // --- Brave Search → FAILED ---

    @Test
    void lookup_savesFailed_whenAllBraveStagesEmpty() {
        when(repo.findByLookupTerm(any())).thenReturn(Optional.empty());
        when(quotaService.checkQuota(any())).thenReturn(QuotaStatus.OK);
        when(prefService.getQueryKeywords(any())).thenReturn(List.of());
        lenient().when(prefService.getMandatoryFields(any())).thenReturn(List.of());
        when(braveSearchService.search(any(), any())).thenReturn(List.of());

        ProductLookupResult result = service.lookup("Unbekanntes Gerät X999", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.FAILED);
        verify(repo).save(argThat(l -> l.getLookupStatus() == LookupStatus.FAILED));
        verifyNoInteractions(groqExtractionService);
    }

    // --- Vollständiger Happy Path ---

    @Test
    void lookup_savesComplete_onSuccessfulFlow() {
        when(repo.findByLookupTerm(any())).thenReturn(Optional.empty());
        when(quotaService.checkQuota(any())).thenReturn(QuotaStatus.OK);
        when(prefService.getQueryKeywords(any())).thenReturn(List.of("cpu", "ram"));
        when(prefService.getMandatoryFields(any())).thenReturn(List.of("cpu", "ram"));
        when(braveSearchService.search(any(), any()))
                .thenReturn(List.of(braveResult("https://icecat.biz/p/lenovo-12345678.html")));
        when(groqExtractionService.extractFromSnippets(any(), any()))
                .thenReturn(quickFacts("{\"cpu\":\"Core Ultra 7\"}", "12345678"));

        ProductLookupResult result = service.lookup("Lenovo Yoga 7", null);

        assertThat(result.getStatus()).isEqualTo(LookupStatus.COMPLETE);
        verify(repo).save(argThat(l ->
                l.getLookupStatus() == LookupStatus.COMPLETE
                && "12345678".equals(l.getIcecatId())
                && l.getQuickFactsJson() != null && l.getQuickFactsJson().contains("Core Ultra 7")));
    }

    @Test
    void lookup_passesPreferenceKeywords_toBraveSearch() {
        when(repo.findByLookupTerm(any())).thenReturn(Optional.empty());
        when(quotaService.checkQuota(any())).thenReturn(QuotaStatus.OK);
        when(prefService.getQueryKeywords(any())).thenReturn(List.of("cpu", "display"));
        lenient().when(prefService.getMandatoryFields(any())).thenReturn(List.of());
        when(braveSearchService.search(any(), any())).thenReturn(List.of());

        service.lookup("ThinkPad", mock(WhCategory.class));

        verify(braveSearchService).search(eq("ThinkPad"), eq(List.of("cpu", "display")));
    }

    // --- Hilfsmethoden ---

    private ProductLookup lookup(LookupStatus status, String quickFactsJson) {
        return ProductLookup.builder()
                .lookupStatus(status)
                .quickFactsJson(quickFactsJson)
                .build();
    }

    private BraveResult braveResult(String url) {
        return BraveResult.builder()
                .title("Test")
                .url(url)
                .description("desc")
                .extraSnippets(List.of())
                .build();
    }

    private QuickFactsResult quickFacts(String json, String icecatId) {
        QuickFactsResult qfr = new QuickFactsResult();
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = new ObjectMapper().readValue(json, Map.class);
            qfr.setQuickFacts(map);
        } catch (Exception ignored) {}
        qfr.getSources().setIcecatId(icecatId);
        return qfr;
    }
}
