package at.querchecker.research;

import at.querchecker.config.RequestUserAgentResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static at.querchecker.research.entity.SourceType.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class HtmlFetchServiceTest {

    @Mock RequestUserAgentResolver uaResolver;
    @InjectMocks HtmlFetchService service;

    @Test
    void shouldFetchFullPage_trueForFlatpanelshd() {
        assertThat(service.shouldFetchFullPage(FLATPANELSHD)).isTrue();
    }

    @Test
    void shouldFetchFullPage_trueForGsmarena() {
        assertThat(service.shouldFetchFullPage(GSMARENA)).isTrue();
    }

    @Test
    void shouldFetchFullPage_falseForIcecat() {
        assertThat(service.shouldFetchFullPage(ICECAT)).isFalse();
    }

    @Test
    void shouldFetchFullPage_falseForGeneric() {
        assertThat(service.shouldFetchFullPage(GENERIC)).isFalse();
    }
}
