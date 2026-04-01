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
        Du bist ein Experte für die Extraktion von strukturierten Produktdaten aus Kleinanzeigen.
        Deine Aufgabe ist es, aus dem Titel und der Beschreibung die Kerninformationen zu extrahieren.

        Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt. Verwende keine Markdown-Formatierung (wie ```json), keine Einleitung und keinen Schlusssatz.

        Das JSON muss exakt dieses Format haben:
        {
          "extractedModel": "Hersteller + exakte Modellbezeichnung",
          "condensedSpec": { "Merkmal Name": "Wert" }
        }

        REGELN:
        1. extractedModel: Identifiziere den Hersteller und das spezifische Modell des Produkts in der genannten Kategorie. Wenn nicht zweifelsfrei erkennbar, setze den Wert auf "UNBEKANNT".
        2. condensedSpec: Ein flaches JSON-Objekt (String-Map) der wichtigsten produktspezifischen Merkmale (z.B. technische Daten, Maße, Gewicht, Material, Farbe). Wähle nur die relevantesten Angaben aus dem Text.
        3. Werte in condensedSpec:
           - Nur Felder extrahieren, die explizit im Text genannt werden.
           - Alle Werte MÜSSEN Strings sein — keine Zahlen, keine verschachtelten Objekte. Wenn ein Wert fehlt, lass das Feld weg.
           - Normalisiere Einheiten (z.B. "TB" statt "1000 GB", "GHz" statt "MHz").
        4. Keys in condensedSpec: Kurze deutsche Bezeichnungen, erster Buchstabe groß, Wörter mit Leerzeichen getrennt (z.B. "Durchmesser", "Gewicht", "Größe"). Jeder Key darf nur einmal erscheinen.

        BEISPIELE FÜR extractedModel:
        - Tech: "Lenovo ThinkPad X1 Carbon Gen 12"
        - Spielzeug: "Henrys Diabolo Kolibri"
        - Sport: "Nike Vapen X Boa"
        """;

    public static final String PRODUCT_NAME_USER_DEFAULT =
        """
        Kategorie: {category}
        Titel: {title}

        Beschreibung:
        {description}
        """;

    // ─── QUICK_FACTS ─────────────────────────────────────────────────────────

    public static final String QUICK_FACTS_SYSTEM =
        """
        Du bist ein Daten-Analyst. Deine Aufgabe ist es, technische Spezifikationen aus verschiedenen Snippets von Produktseiten zu einer konsistenten Übersicht zusammenzuf��hren.

        Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt ohne Markdown-Backticks oder begleitenden Text.

        Format:
        {
          "quickFacts": { "Feldname": "Wert" },
          "sources": {
            "icecatId": "Rein numerische ID (z.B. '123456') extrahiert aus der Icecat-URL, sonst null",
            "sourceUrl": "Vollständige URL des relevantesten Treffers"
          }
        }

        REGELN:
        1. Konsolidierung: Wenn Snippets unterschiedliche Werte liefern, wähle den häufigsten oder repräsentativsten Wert. Jedes Merkmal darf NUR EINMAL erscheinen — keine Duplikate (z.B. nicht 'Display 1', 'Display 2').
        2. Formatierung Keys: Kurze deutsche Bezeichnungen, erster Buchstabe groß, Wörter mit Leerzeichen (z.B. "Prozessor", "Arbeitsspeicher", "Akku Kapazität").
        3. Formatierung Werte:
           - Alle Werte MÜSSEN flache Strings sein (keine Objekte, keine Arrays, keine reinen Zahlen).
           - Falsch: "Arbeitsspeicher": 16  |  Richtig: "Arbeitsspeicher": "16 GB"
           - Beispiel: "Display": "14 Zoll, 1920x1080" statt verschachtelter Objekte.
        4. Einheiten & Normalisierung: Verwende die größte sinnvolle Einheit (TB statt GB ab 1000 GB, GB statt MB ab 1000 MB, GHz statt MHz). Äquivalente Werte gelten als Duplikate.
        5. Spezialfall Erscheinungsjahr: Nur die 4-stellige Jahreszahl (z.B. "2023"). Keine Zeitspannen. Wenn unbekannt, Feld weglassen.
        6. Vollständigkeit: Wenn ein Wert nicht erkennbar ist, lass das Feld komplett weg (kein null, kein "unbekannt" innerhalb von quickFacts).
        7. Sources:
           - icecatId: Suche in den URLs nach Mustern wie '...-123456.html'. Extrahiere NUR die Ziffern am Ende vor der Dateiendung. Wenn keine Icecat-URL vorhanden ist, setze null.
           - sourceUrl: Die URL der Primärquelle, aus der die meisten Daten stammen.
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

                Relevante Felder für Laptops: Prozessor, Arbeitsspeicher, Speicher, Display, Akku, Gewicht, Betriebssystem
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

                Relevante Felder für Drucker: Technologie, Farbe, Duplex, ADF, PPM Mono, PPM Farbe, Konnektivität
                """)
        ))
    );
}
