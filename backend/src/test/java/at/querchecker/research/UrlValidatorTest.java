package at.querchecker.research;

import at.querchecker.research.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static at.querchecker.research.entity.SourceType.*;
import static org.assertj.core.api.Assertions.assertThat;

class UrlValidatorTest {

    UrlValidator validator = new UrlValidator();

    // --- resolveSourceUrl ---

    @Test
    void resolveSourceUrl_returnsLlmUrl_whenFoundInBraveResults() {
        List<SearchResult> results = List.of(
                result("https://icecat.biz/p/lenovo-thinkpad-12345.html"),
                result("https://icecat.biz/p/lenovo-other-99999.html"));

        assertThat(validator.resolveSourceUrl(
                "https://icecat.biz/p/lenovo-thinkpad-12345.html", results))
                .isEqualTo("https://icecat.biz/p/lenovo-thinkpad-12345.html");
    }

    @Test
    void resolveSourceUrl_returnsTopBraveUrl_whenLlmUrlNotInResults() {
        List<SearchResult> results = List.of(
                result("https://icecat.biz/p/lenovo-12345.html"));

        assertThat(validator.resolveSourceUrl("https://halluziniert.com/xyz", results))
                .isEqualTo("https://icecat.biz/p/lenovo-12345.html");
    }

    @Test
    void resolveSourceUrl_returnsNull_whenResultsEmpty() {
        assertThat(validator.resolveSourceUrl("https://irgendwas.com", List.of()))
                .isNull();
    }

    @Test
    void resolveSourceUrl_caseInsensitiveMatch() {
        List<SearchResult> results = List.of(
                result("https://ICECAT.BIZ/p/lenovo-12345.html"));

        assertThat(validator.resolveSourceUrl(
                "https://icecat.biz/p/lenovo-12345.html", results))
                .isEqualTo("https://icecat.biz/p/lenovo-12345.html");
    }

    // --- resolveIcecatId ---

    @Test
    void resolveIcecatId_returnsId_whenFoundInUrls() {
        List<SearchResult> results = List.of(
                result("https://icecat.biz/p/lenovo-thinkpad-12345.html"));

        assertThat(validator.resolveIcecatId("12345", results))
                .isEqualTo("12345");
    }

    @Test
    void resolveIcecatId_returnsNull_whenIdNotInUrls() {
        List<SearchResult> results = List.of(
                result("https://icecat.biz/p/lenovo-thinkpad-12345.html"));

        assertThat(validator.resolveIcecatId("99999", results)).isNull();
    }

    @Test
    void resolveIcecatId_returnsNull_whenIdIsNull() {
        assertThat(validator.resolveIcecatId(null, List.of())).isNull();
    }

    // --- matchesExpectedPattern ---

    @Test
    void matchesExpectedPattern_icecat_simpleFormat() {
        assertThat(validator.matchesExpectedPattern(
                "https://icecat.biz/p/lenovo-thinkpad-x1-12345.html", ICECAT))
                .isTrue();
    }

    @Test
    void matchesExpectedPattern_icecat_withLanguageCode() {
        assertThat(validator.matchesExpectedPattern(
                "https://icecat.biz/de-ch/p/lenovo-thinkpad-12345.html", ICECAT))
                .isTrue();
    }

    @Test
    void matchesExpectedPattern_icecat_withLanguageCodeAndBrandPath() {
        assertThat(validator.matchesExpectedPattern(
                "https://icecat.biz/de-ch/p/lenovo/20l90043ge/thinkpad-notebooks-t580-73311950.html", ICECAT))
                .isTrue();
    }

    @Test
    void matchesExpectedPattern_icecat_enLanguageCode() {
        assertThat(validator.matchesExpectedPattern(
                "https://icecat.biz/en/p/samsung-galaxy-s25-13322.html", ICECAT))
                .isTrue();
    }

    @Test
    void matchesExpectedPattern_icecat_invalidUrl_noId() {
        assertThat(validator.matchesExpectedPattern(
                "https://icecat.biz/search/?q=thinkpad", ICECAT))
                .isFalse();
    }

    @Test
    void matchesExpectedPattern_gsmarena_validUrl() {
        assertThat(validator.matchesExpectedPattern(
                "https://www.gsmarena.com/samsung_galaxy_s25_ultra-13322.php", GSMARENA))
                .isTrue();
    }

    @Test
    void matchesExpectedPattern_flatpanelshd_validDbPage() {
        assertThat(validator.matchesExpectedPattern(
                "https://www.flatpanelshd.com/lg_g5_oled_2025.php", FLATPANELSHD))
                .isTrue();
    }

    @Test
    void matchesExpectedPattern_flatpanelshd_hyphenatedModelName_valid() {
        assertThat(validator.matchesExpectedPattern(
                "https://www.flatpanelshd.com/samsung-qe65qn85b.php", FLATPANELSHD))
                .isTrue();
    }

    @Test
    void matchesExpectedPattern_flatpanelshd_reviewPage_invalid() {
        // review.php sollte durch queryExcludes schon rausgefiltert sein,
        // Pattern-Check als zusätzliches Sicherheitsnetz
        assertThat(validator.matchesExpectedPattern(
                "https://www.flatpanelshd.com/review.php?id=123", FLATPANELSHD))
                .isFalse();
    }

    @Test
    void matchesExpectedPattern_generic_alwaysTrue() {
        assertThat(validator.matchesExpectedPattern(
                "https://irgendwas.com/produkt", GENERIC))
                .isTrue();
    }

    @Test
    void matchesExpectedPattern_nullUrl_returnsFalse() {
        assertThat(validator.matchesExpectedPattern(null, ICECAT)).isFalse();
    }

    // --- normalizeUrl ---

    @Test
    void normalizeUrl_convertsProtocolRelativeUrl() {
        assertThat(validator.normalizeUrl("//icecat.biz/p/lenovo-12345.html"))
                .isEqualTo("https://icecat.biz/p/lenovo-12345.html");
    }

    @Test
    void normalizeUrl_convertsProtocolRelativeUrlWithLanguageCode() {
        assertThat(validator.normalizeUrl("//icecat.biz/de-ch/p/lenovo/20l90043ge/thinkpad-t580-73311950.html"))
                .isEqualTo("https://icecat.biz/de-ch/p/lenovo/20l90043ge/thinkpad-t580-73311950.html");
    }

    @Test
    void normalizeUrl_passesThroughAbsoluteUrl() {
        String absoluteUrl = "https://gsmarena.com/samsung_galaxy_s25-13322.php";
        assertThat(validator.normalizeUrl(absoluteUrl)).isEqualTo(absoluteUrl);
    }

    @Test
    void normalizeUrl_returnsNullForNullInput() {
        assertThat(validator.normalizeUrl(null)).isNull();
    }

    @Test
    void normalizeUrl_returnsNullForBlankInput() {
        assertThat(validator.normalizeUrl("   ")).isNull();
    }

    private SearchResult result(String url) {
        return new SearchResult(null, url, null, List.of());
    }
}
