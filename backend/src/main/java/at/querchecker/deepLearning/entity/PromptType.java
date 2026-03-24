package at.querchecker.deepLearning.entity;

public enum PromptType {
    PRODUCT_NAME,    // Produktname aus Inseratstext extrahieren
    QUICK_FACTS,     // Technische Specs aus Brave-Snippets extrahieren (ICECAT, GENERIC)
    HTML_FULL_SPECS  // Gruppierte Specs aus HTML-Fetch (GSMARENA, FLATPANELSHD)
}
