package at.querchecker.research.entity;

public enum SourceType {
    ICECAT,        // icecatId-Extraktion + Full-Specs-Button via live.icecat.biz API
    FLATPANELSHD,  // HTML-Fetch via Jsoup + LLM; TV/HiFi
    GSMARENA,      // HTML-Fetch via Jsoup + LLM; Smartphones
    GENERIC        // Nur Brave-Snippets; kein HTML-Fetch
}
