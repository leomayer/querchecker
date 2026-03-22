package at.querchecker.deepLearning;

import java.util.Map;

/**
 * Zentrale Definition aller Kategorie-Prompts.
 * DlCategoryPromptSeeder liest diese Konstanten und befüllt die DB idempotent.
 * Kategorienamen müssen exakt mit WhCategory.name übereinstimmen.
 */
public final class DlCategoryPromptDefinitions {

    private DlCategoryPromptDefinitions() {}

    // ─── PRODUCT_NAME ────────────────────────────────────────────────────────

    public static final String PRODUCT_NAME_SYSTEM =
        """
        Du extrahierst Produktbezeichnungen aus Kleinanzeigen-Texten.
        Antworte NUR mit dem Produktnamen aus dem gegebenen Inserat — kein erklärender Text, keine Listen, keine Sätze.
        Erfinde keinen Produktnamen. Wenn kein eindeutiger Produktname im Inserat erkennbar ist, antworte mit: UNBEKANNT
        """;

    public static final String PRODUCT_NAME_USER_DEFAULT =
        """
        Kategorie: {category}
        Titel: {title}

        Beschreibung:
        {description}

        Extrahiere den genauen Produktnamen oder die Modellbezeichnung.
        Beispiele für gute Antworten:
        - ThinkPad X1 Carbon Gen 12
        - Samsung Galaxy S24 Ultra
        - HP LaserJet Pro M404dn
        """;

    /**
     * Kategorie-spezifische User-Prompts für PRODUCT_NAME (optional).
     * Keys müssen exakt mit wh_category.name übereinstimmen (beliebige Ebene).
     */
    public static final Map<String, String> PRODUCT_NAME_USER_BY_CATEGORY = Map.ofEntries(
        Map.entry("Computer / Software",
            """
            Kategorie: {category}
            Titel: {title}

            Beschreibung:
            {description}

            Welches {category} wird verkauft? Nenne Hersteller und Modellbezeichnung.
            """),
        Map.entry("Smartphones / Telefonie",
            """
            Kategorie: {category}
            Titel: {title}

            Beschreibung:
            {description}

            Welches {category} wird verkauft? Nenne Hersteller und Modellbezeichnung.
            """),
        Map.entry("Kameras / TV / Multimedia",
            """
            Kategorie: {category}
            Titel: {title}

            Beschreibung:
            {description}

            Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung.
            """),
        Map.entry("Games / Konsolen",
            """
            Kategorie: {category}
            Titel: {title}

            Beschreibung:
            {description}

            Welches {category} wird angeboten? Nenne den genauen Namen.
            """),
        Map.entry("Wohnen / Haushalt / Gastronomie",
            """
            Kategorie: {category}
            Titel: {title}

            Beschreibung:
            {description}

            Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung.
            """),
        Map.entry("Haus / Garten / Werkstatt",
            """
            Kategorie: {category}
            Titel: {title}

            Beschreibung:
            {description}

            Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung.
            """),
        Map.entry("Freizeit / Instrumente / Kulinarik",
            """
            Kategorie: {category}
            Titel: {title}

            Beschreibung:
            {description}

            Welches {category} wird angeboten? Nenne den genauen Produktnamen.
            """),
        Map.entry("Sport / Sportgeräte",
            """
            Kategorie: {category}
            Titel: {title}

            Beschreibung:
            {description}

            Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung.
            """)
    );

    // ─── QUICK_FACTS ─────────────────────────────────────────────────────────

    public static final String QUICK_FACTS_SYSTEM =
        """
        Du extrahierst technische Spezifikationen aus Produktseiten-Snippets.
        Antworte NUR mit validem JSON — kein erklärender Text, keine Markdown-Backticks.
        Wenn ein Wert nicht erkennbar ist, lass das Feld weg (kein null, kein "unbekannt").
        Feldnamen im quickFacts-Objekt: Kleinbuchstaben, Englisch, keine Sonderzeichen.
        """;

    public static final String QUICK_FACTS_USER_DEFAULT =
        """
        Produkt: {lookupTerm}
        Kategorie: {category}

        Suchergebnisse:
        {snippets}

        Pflichtfelder (müssen erscheinen wenn erkennbar):
        {mandatoryFields}

        Antworte mit diesem JSON-Schema:
        {
          "quickFacts": {
            "cpu": "...",
            "ram": "..."
          },
          "sources": {
            "icecatId": "die rein numerische ID am Ende der icecat-URL, direkt vor .html — Beispiel: aus '...lenovo-thinkpad-x1-carbon-123456.html' ist die icecatId '123456'",
            "icecatUrl": "vollständige URL des relevantesten Treffers"
          }
        }
        """;

    /**
     * Kategorie-spezifische User-Prompts für QUICK_FACTS (optional).
     * Keys müssen exakt mit wh_category.name übereinstimmen (beliebige Ebene).
     */
    public static final Map<String, String> QUICK_FACTS_USER_BY_CATEGORY = Map.ofEntries(
        Map.entry("Laptop / Notebook",
            """
            Produkt: {lookupTerm}
            Kategorie: Laptop / Notebook

            Suchergebnisse:
            {snippets}

            Pflichtfelder (müssen erscheinen wenn erkennbar):
            {mandatoryFields}

            Relevante Felder für Laptops: cpu, ram, storage, display, battery, weight, os

            Antworte mit diesem JSON-Schema:
            {
              "quickFacts": { "cpu": "...", "ram": "...", "display": "..." },
              "sources": {
                "icecatId": "die rein numerische ID am Ende der icecat-URL, direkt vor .html — Beispiel: aus '...thinkpad-t14-123456.html' ist die icecatId '123456'",
                "icecatUrl": "vollständige URL des relevantesten Treffers"
              }
            }
            """),
        Map.entry("Drucker & Scanner",
            """
            Produkt: {lookupTerm}
            Kategorie: Drucker & Scanner

            Suchergebnisse:
            {snippets}

            Pflichtfelder (müssen erscheinen wenn erkennbar):
            {mandatoryFields}

            Relevante Felder für Drucker: technology, color, duplex, adf, ppm_mono, ppm_color, connectivity

            Antworte mit diesem JSON-Schema:
            {
              "quickFacts": { "technology": "...", "duplex": "...", "ppm_mono": "..." },
              "sources": { "icecatId": "...", "icecatUrl": "..." }
            }
            """)
    );
}
