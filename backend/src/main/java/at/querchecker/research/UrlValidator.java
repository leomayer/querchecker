package at.querchecker.research;

import at.querchecker.research.entity.SourceType;
import at.querchecker.research.model.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class UrlValidator {

    private static final Map<SourceType, Pattern> URL_PATTERNS = Map.of(
            SourceType.ICECAT,       Pattern.compile(
                    "https://icecat\\.biz/p/[\\w\\-]+-\\d+\\.html"),
            SourceType.GSMARENA,     Pattern.compile(
                    "https://(?:www\\.)?gsmarena\\.com/[\\w_]+-\\d+\\.php"),
            SourceType.FLATPANELSHD, Pattern.compile(
                    "https://(?:www\\.)?flatpanelshd\\.com/[\\w_]+\\.php")
    );

    /**
     * Validiert die vom LLM gelieferte sourceUrl gegen die echten Brave-Ergebnisse.
     * Bei Fehler: Fallback auf Top-Brave-URL (immer real).
     */
    public String resolveSourceUrl(String llmUrl, List<SearchResult> braveResults) {
        if (llmUrl != null && braveResults.stream()
                .anyMatch(r -> r.getUrl().equalsIgnoreCase(llmUrl))) {
            return llmUrl;
        }
        return braveResults.isEmpty() ? null : braveResults.get(0).getUrl();
    }

    /**
     * Validiert die vom LLM gelieferte icecatId gegen die Brave-URLs.
     * Verhindert Halluzinationen — ID muss in mindestens einer URL vorkommen.
     */
    public String resolveIcecatId(String llmId, List<SearchResult> braveResults) {
        if (llmId == null) return null;
        boolean valid = braveResults.stream()
                .anyMatch(r -> r.getUrl().contains(llmId));
        return valid ? llmId : null;
    }

    /**
     * Prüft ob eine URL dem erwarteten Pattern für einen SourceType entspricht.
     * Wird vor HTML-Fetches ausgeführt um unnötige HTTP-Calls auf Review/News-Seiten
     * zu vermeiden. GENERIC hat kein Pattern → immer gültig.
     */
    public boolean matchesExpectedPattern(String url, SourceType sourceType) {
        if (url == null) return false;
        Pattern pattern = URL_PATTERNS.get(sourceType);
        return pattern == null || pattern.matcher(url).matches();
    }
}
