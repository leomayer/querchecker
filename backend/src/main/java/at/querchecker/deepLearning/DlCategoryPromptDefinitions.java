package at.querchecker.deepLearning;

import java.util.Map;

/**
 * Zentrale Definition aller Kategorie-Prompts.
 * Neue Kategorien hier ergänzen – DlCategoryPromptSeeder liest diese Liste.
 * Kategorienamen müssen exakt mit WhCategory.name übereinstimmen.
 */
public final class DlCategoryPromptDefinitions {

    private DlCategoryPromptDefinitions() {}

    /** Fallback wenn keine Kategorie passt */
    public static final String DEFAULT =
        "Was ist der genaue Produktname oder die Modellbezeichnung?";

    /**
     * Keys müssen exakt mit wh_category.name (level=0) übereinstimmen.
     * Aktuelle Willhaben-Kategorien: SELECT name FROM wh_category WHERE level = 0 ORDER BY name;
     *
     * Prompts können {category} als Platzhalter enthalten – DlPromptResolver ersetzt diesen
     * zur Laufzeit mit dem tiefsten (spezifischsten) Kategorie-Namen des Inserats.
     * Beispiel: Kategorie "Notebooks" → "Welches Notebooks wird verkauft? ..."
     */
    public static final Map<String, String> CATEGORY_PROMPTS = Map.ofEntries(
        // Computer / Software
        Map.entry("Computer / Software",
            "Welches {category} wird verkauft? Nenne Hersteller und Modellbezeichnung."),
        // Smartphones / Telefonie
        Map.entry("Smartphones / Telefonie",
            "Welches {category} wird verkauft? Nenne Hersteller und Modellbezeichnung."),
        // Kameras / TV / Multimedia
        Map.entry("Kameras / TV / Multimedia",
            "Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung."),
        // Games / Konsolen
        Map.entry("Games / Konsolen",
            "Welches {category} wird angeboten? Nenne den genauen Namen."),
        // Wohnen / Haushalt / Gastronomie
        Map.entry("Wohnen / Haushalt / Gastronomie",
            "Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung."),
        // Haus / Garten / Werkstatt
        Map.entry("Haus / Garten / Werkstatt",
            "Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung."),
        // Freizeit / Instrumente / Kulinarik
        Map.entry("Freizeit / Instrumente / Kulinarik",
            "Welches {category} wird angeboten? Nenne den genauen Produktnamen."),
        // Sport / Sportgeräte
        Map.entry("Sport / Sportgeräte",
            "Welches {category} wird angeboten? Nenne Hersteller und Modellbezeichnung.")
    );
}
