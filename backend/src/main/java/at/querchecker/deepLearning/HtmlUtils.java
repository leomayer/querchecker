package at.querchecker.deepLearning;

import org.jsoup.Jsoup;

/**
 * Utility for extracting plain text from HTML content.
 * Used in text processing pipelines for NLP/extraction models.
 */
public final class HtmlUtils {

    private HtmlUtils() {}

    /**
     * Strips HTML tags and returns plain text.
     * @param html HTML string, may be null
     * @return Plain text with whitespace normalized
     */
    public static String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text();
    }
}
