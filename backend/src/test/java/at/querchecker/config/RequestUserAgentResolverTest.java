package at.querchecker.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static at.querchecker.config.RequestUserAgentResolver.HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestUserAgentResolverTest {

    @Mock
    UserAgentHolder userAgentHolder;

    @InjectMocks
    RequestUserAgentResolver resolver;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsForwardedUaWhenHeaderPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "Mozilla/5.0 (Browser UA)");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(resolver.resolve()).isEqualTo("Mozilla/5.0 (Browser UA)");
    }

    @Test
    void fallsBackToUserAgentHolderWhenHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(userAgentHolder.get()).thenReturn("Fallback UA");

        assertThat(resolver.resolve()).isEqualTo("Fallback UA");
    }

    @Test
    void fallsBackToUserAgentHolderWhenHeaderBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "   ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(userAgentHolder.get()).thenReturn("Fallback UA");

        assertThat(resolver.resolve()).isEqualTo("Fallback UA");
    }

    @Test
    void fallsBackToUserAgentHolderWhenNoRequestContext() {
        when(userAgentHolder.get()).thenReturn("Fallback UA");

        assertThat(resolver.resolve()).isEqualTo("Fallback UA");
    }
}
