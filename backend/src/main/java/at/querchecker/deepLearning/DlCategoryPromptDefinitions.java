package at.querchecker.deepLearning;

import at.querchecker.deepLearning.entity.PromptType;

import java.util.List;
import java.util.Map;

/**
 * Zentrale Definition aller Kategorie-Prompts.
 * DlCategoryPromptSeeder liest diese Konstanten und befüllt die DB idempotent.
 * Kategorienamen müssen exakt mit WhCategory.name übereinstimmen.
 */
public final class DlCategoryPromptDefinitions {

    private DlCategoryPromptDefinitions() {}

    public record PromptConfig(PromptType promptType, String systemPrompt, String userPrompt) {}

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
            "field_name": "extracted value",
            "another_field": "extracted value"
          },
          "sources": {
            "icecatId": "die rein numerische ID am Ende der icecat-URL, direkt vor .html — Beispiel: aus '...lenovo-thinkpad-x1-carbon-123456.html' ist die icecatId '123456'",
            "icecatUrl": "vollständige URL des relevantesten Treffers"
          }
        }
        """;

    // ─── CONFIGS (kategorie-spezifisch, alle PromptTypes) ────────────────────

    /**
     * Kategorie-spezifische Prompts für alle PromptTypes.
     * Keys müssen exakt mit wh_category.name übereinstimmen (beliebige Ebene).
     * DlCategoryPromptSeeder iteriert über PromptType.values() und prüft pro Eintrag.
     */
    public static final Map<String, List<PromptConfig>> CONFIGS = Map.ofEntries(
        Map.entry("Computer / Software", List.of(
            new PromptConfig(PromptType.PRODUCT_NAME, PRODUCT_NAME_SYSTEM,
                """
                Kategorie: {category}
                Titel: {title}

                Beschreibung:
                {description}

                Welches {category} wird verkauft? Nenne Hersteller und Modellbezeichnung.
                """)
        )),
        Map.entry("Smartphones / Telefonie", List.of(
            new PromptConfig(PromptType.PRODUCT_NAME, PRODUCT_NAME_SYSTEM,
                """
                Kategorie: {category}
                Titel: {title}

                Beschreibung:
                {description}

                Welches {category} wird verkauft? Nenne Hersteller und Modellbezeichnung.
                """)
        )),
        Map.entry("Kameras / TV / Multimedia", List.of(
            new PromptConfig(PromptType.PRODUCT_NAME, PRODUCT_NAME_SYSTEM,
                """
                Kategorie: {category}
                Titel: {title}

                Beschreibung:
                {description}

                Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung.
                """)
        )),
        Map.entry("Games / Konsolen", List.of(
            new PromptConfig(PromptType.PRODUCT_NAME, PRODUCT_NAME_SYSTEM,
                """
                Kategorie: {category}
                Titel: {title}

                Beschreibung:
                {description}

                Welches {category} wird angeboten? Nenne den genauen Namen.
                """)
        )),
        Map.entry("Wohnen / Haushalt / Gastronomie", List.of(
            new PromptConfig(PromptType.PRODUCT_NAME, PRODUCT_NAME_SYSTEM,
                """
                Kategorie: {category}
                Titel: {title}

                Beschreibung:
                {description}

                Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung.
                """)
        )),
        Map.entry("Haus / Garten / Werkstatt", List.of(
            new PromptConfig(PromptType.PRODUCT_NAME, PRODUCT_NAME_SYSTEM,
                """
                Kategorie: {category}
                Titel: {title}

                Beschreibung:
                {description}

                Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung.
                """)
        )),
        Map.entry("Freizeit / Instrumente / Kulinarik", List.of(
            new PromptConfig(PromptType.PRODUCT_NAME, PRODUCT_NAME_SYSTEM,
                """
                Kategorie: {category}
                Titel: {title}

                Beschreibung:
                {description}

                Welches {category} wird angeboten? Nenne den genauen Produktnamen.
                """)
        )),
        Map.entry("Sport / Sportgeräte", List.of(
            new PromptConfig(PromptType.PRODUCT_NAME, PRODUCT_NAME_SYSTEM,
                """
                Kategorie: {category}
                Titel: {title}

                Beschreibung:
                {description}

                Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung.
                """)
        )),
        Map.entry("Laptop / Notebook", List.of(
            new PromptConfig(PromptType.QUICK_FACTS, QUICK_FACTS_SYSTEM,
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
                """)
        )),
        Map.entry("Drucker & Scanner", List.of(
            new PromptConfig(PromptType.QUICK_FACTS, QUICK_FACTS_SYSTEM,
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
        ))
    );
}
