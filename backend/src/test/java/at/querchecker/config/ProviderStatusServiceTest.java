package at.querchecker.config;

import org.junit.jupiter.api.Test;

import static at.querchecker.config.ProviderStatusService.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-Tests für ProviderStatusService.isUnconfigured().
 * Keine Spring-Kontext-Abhängigkeit — reine Logik-Tests.
 */
class ProviderStatusServiceTest {

    // --- isUnconfigured: API-Key-Varianten ---

    @Test
    void nullKeyIsUnconfigured() {
        assertThat(isUnconfigured(null, BRAVE_PLACEHOLDER)).isTrue();
    }

    @Test
    void blankKeyIsUnconfigured() {
        assertThat(isUnconfigured("   ", BRAVE_PLACEHOLDER)).isTrue();
    }

    @Test
    void emptyKeyIsUnconfigured() {
        assertThat(isUnconfigured("", BRAVE_PLACEHOLDER)).isTrue();
    }

    @Test
    void bravePlaceholderIsUnconfigured() {
        assertThat(isUnconfigured(BRAVE_PLACEHOLDER, BRAVE_PLACEHOLDER)).isTrue();
    }

    @Test
    void groqPlaceholderIsUnconfigured() {
        assertThat(isUnconfigured(GROQ_PLACEHOLDER, GROQ_PLACEHOLDER)).isTrue();
    }

    @Test
    void openrouterPlaceholderIsUnconfigured() {
        assertThat(isUnconfigured(OPENROUTER_PLACEHOLDER, OPENROUTER_PLACEHOLDER)).isTrue();
    }

    @Test
    void realBraveKeyIsConfigured() {
        assertThat(isUnconfigured("BSAxyz123", BRAVE_PLACEHOLDER)).isFalse();
    }

    @Test
    void realGroqKeyIsConfigured() {
        assertThat(isUnconfigured("gsk_real_groq_key_abc", GROQ_PLACEHOLDER)).isFalse();
    }

    @Test
    void realOpenRouterKeyIsConfigured() {
        assertThat(isUnconfigured("sk-or-v1-real-key", OPENROUTER_PLACEHOLDER)).isFalse();
    }

    // --- isUnconfigured: Google credentials-path ---

    @Test
    void googleCredentialsPlaceholderIsUnconfigured() {
        assertThat(isUnconfigured(GOOGLE_CREDENTIALS_PLACEHOLDER, GOOGLE_CREDENTIALS_PLACEHOLDER)).isTrue();
    }

    @Test
    void realCredentialsPathIsConfigured() {
        assertThat(isUnconfigured("credentials/gcp-creds.json", GOOGLE_CREDENTIALS_PLACEHOLDER)).isFalse();
    }

    @Test
    void absoluteCredentialsPathIsConfigured() {
        assertThat(isUnconfigured("/etc/querchecker/credentials.json", GOOGLE_CREDENTIALS_PLACEHOLDER)).isFalse();
    }

    // --- cross-placeholder: wrong placeholder must not match ---

    @Test
    void bravePlaceholderDoesNotMatchGroq() {
        assertThat(isUnconfigured(BRAVE_PLACEHOLDER, GROQ_PLACEHOLDER)).isFalse();
    }

    @Test
    void groqPlaceholderDoesNotMatchBrave() {
        assertThat(isUnconfigured(GROQ_PLACEHOLDER, BRAVE_PLACEHOLDER)).isFalse();
    }
}
