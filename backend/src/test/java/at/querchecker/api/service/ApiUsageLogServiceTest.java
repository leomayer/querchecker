package at.querchecker.api.service;

import at.querchecker.api.entity.Provider;
import at.querchecker.api.entity.RequestType;
import at.querchecker.api.repository.ApiUsageLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class ApiUsageLogServiceTest {

    @Mock ApiUsageLogRepository repo;
    @InjectMocks ApiUsageLogService service;

    @Test
    void log_savesEntryWithAllFields() {
        service.log(Provider.BRAVE, RequestType.SEARCH, "ThinkPad X1", 200, null, null, 320L);

        verify(repo).save(argThat(entry ->
            entry.getProvider()       == Provider.BRAVE        &&
            entry.getRequestType()    == RequestType.SEARCH    &&
            entry.getLookupTerm()     .equals("ThinkPad X1")  &&
            entry.getResponseStatus() == 200                   &&
            entry.getDurationMs()     == 320L                  &&
            entry.getCreatedAt()      != null
        ));
    }

    @Test
    void log_acceptsNullTokens_forBraveSearch() {
        // Brave liefert keine Token-Counts
        assertThatNoException().isThrownBy(() ->
            service.log(Provider.BRAVE, RequestType.SEARCH, "test", 200, null, null, 100L));
    }

    @Test
    void log_savesTokens_forGroqExtraction() {
        service.log(Provider.GROQ, RequestType.EXTRACTION, "ThinkPad", 200, 950, 150, 850L);

        verify(repo).save(argThat(entry ->
            entry.getTokensInput()  == 950 &&
            entry.getTokensOutput() == 150
        ));
    }

    @Test
    void countByProviderAndPeriod_delegatesToRepository() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to   = LocalDateTime.now();
        when(repo.countByProviderAndCreatedAtBetween(Provider.BRAVE, from, to)).thenReturn(47L);

        assertThat(service.countByProviderAndPeriod(Provider.BRAVE, from, to)).isEqualTo(47L);
    }

    @Test
    void sumTokensByProviderAndPeriod_delegatesToRepository() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to   = LocalDateTime.now();
        when(repo.sumTokensByProviderAndCreatedAtBetween(Provider.GROQ, from, to))
            .thenReturn(13200L);

        assertThat(service.sumTokensByProviderAndPeriod(Provider.GROQ, from, to))
            .isEqualTo(13200L);
    }

    @Test
    void sumTokensByProviderAndPeriod_returnsZero_whenNoEntries() {
        when(repo.sumTokensByProviderAndCreatedAtBetween(any(), any(), any()))
            .thenReturn(null); // SUM() gibt NULL zurück wenn keine Zeilen

        assertThat(service.sumTokensByProviderAndPeriod(Provider.GROQ,
            LocalDateTime.now().minusDays(1), LocalDateTime.now())).isZero();
    }
}
