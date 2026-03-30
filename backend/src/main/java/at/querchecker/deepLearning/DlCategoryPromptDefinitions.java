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
        Du extrahierst Produktinformationen aus Kleinanzeigen-Texten.
        Antworte NUR mit validem JSON — kein erklärender Text, keine Markdown-Backticks.
        Gib folgendes JSON zurück:
        {
          "extractedModel": "Hersteller + genaue Modellbezeichnung",
          "condensedSpec": { "feld": "wert" }
        }
        extractedModel: Hersteller und exakte Modellbezeichnung des verkauften Produkts. Wenn nicht erkennbar: "UNBEKANNT".
        condensedSpec: Die wichtigsten technischen Eckdaten direkt aus dem Inseratstext als flaches String-Map. Nur Felder die im Inserat explizit genannt werden.
        Alle Werte MÜSSEN Strings sein — keine Zahlen, keine verschachtelten Objekte. Wenn ein Wert fehlt, lass das Feld weg.
        Normalisiere Einheiten: TB statt GB ab 1000 GB, GHz statt MHz. Jedes Feld darf NUR EINMAL erscheinen.
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
        Wenn ein Wert nicht erkennbar ist, lass das Feld weg (kein null, kein "unbekannt", kein "kein Wert erkennbar").
        Feldnamen im quickFacts-Objekt: Kleinbuchstaben, Englisch, keine Sonderzeichen.
        Alle Werte im quickFacts-Objekt MÜSSEN Strings sein — keine Zahlen, keine verschachtelten Objekte, keine Arrays.
        Falsch: "ram": 16          Richtig: "ram": "16 GB"
        Falsch: "display": {"size": 14, "resolution": "1920x1080"}   Richtig: "display": "14 Zoll 1920x1080"
        Jedes Feld darf NUR EINMAL erscheinen — keine nummerierten Duplikate wie cpu2, ram3, display4.
        Wenn die Snippets mehrere Varianten desselben Produkts enthalten, wähle den häufigsten oder repräsentativsten Wert.
        Normalisiere Einheiten: Verwende stets die größte sinnvolle Einheit (TB statt GB ab 1000 GB, GB statt MB ab 1000 MB, GHz statt MHz). Äquivalente Werte (z.B. 1 TB und 1000 GB, 16 GB und 16384 MB) gelten als Duplikate — nur einmal ausgeben.
        """;

    public static final String QUICK_FACTS_USER_DEFAULT =
        """
        Produkt: {lookupTerm}
        Kategorie: {category}
        {condensedSpec}
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
            "sourceUrl": "vollständige URL des relevantesten Treffers (egal welche Quelle)"
          }
        }
        """;

    // ─── HTML_FULL_SPECS ──────────────────────────────────────────────────────

    public static final String HTML_FULL_SPECS_SYSTEM =
        """
        Du extrahierst technische Spezifikationen aus einer Produktseite (vollständiger Seitentext oder Tabellen).
        Antworte NUR mit validem JSON — kein erklärender Text, keine Markdown-Backticks.
        Wenn ein Wert nicht erkennbar ist, lass das Feld weg (kein null, kein "unbekannt").
        Feldnamen im quickFacts-Objekt: Kleinbuchstaben, Englisch, keine Sonderzeichen.
        Gruppiere Felder nach ihren Abschnitten in featureGroups (z.B. "Display", "Connectivity").
        """;

    public static final String HTML_FULL_SPECS_USER_DEFAULT =
        """
        Produkt: {lookupTerm}
        Kategorie: {category}

        Seiteninhalt:
        {snippets}

        Pflichtfelder (müssen erscheinen wenn erkennbar):
        {mandatoryFields}

        Antworte mit diesem JSON-Schema:
        {
          "quickFacts": {
            "field_name": "extracted value"
          },
          "featureGroups": [
            {
              "name": "Gruppenname (z.B. Display, Connectivity)",
              "features": [
                { "name": "Feldbezeichnung", "value": "Wert" }
              ]
            }
          ],
          "sources": {
            "icecatId": null
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
                {condensedSpec}
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
                    "sourceUrl": "vollständige URL des relevantesten Treffers (egal welche Quelle)"
                  }
                }
                """)
        )),
        Map.entry("Drucker & Scanner", List.of(
            new PromptConfig(PromptType.QUICK_FACTS, QUICK_FACTS_SYSTEM,
                """
                Produkt: {lookupTerm}
                Kategorie: Drucker & Scanner
                {condensedSpec}
                Suchergebnisse:
                {snippets}

                Pflichtfelder (müssen erscheinen wenn erkennbar):
                {mandatoryFields}

                Relevante Felder für Drucker: technology, color, duplex, adf, ppm_mono, ppm_color, connectivity

                Antworte mit diesem JSON-Schema:
                {
                  "quickFacts": { "technology": "...", "duplex": "...", "ppm_mono": "..." },
                  "sources": { "icecatId": "...", "sourceUrl": "..." }
                }
                """)
        ))
    );
}
