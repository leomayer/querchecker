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

  public static final String PRODUCT_NAME_SYSTEM = """
    Du bist ein Experte für strukturierte Produktdaten-Extraktion. Deine Aufgabe: Extrahiere Kerninformationen aus Titel und Beschreibung einer Kleinanzeige.

    AUSGABE-REGELN:
    - Antworte NUR mit validem JSON.
    - KEIN Markdown (```json), keine Einleitung, kein Schlusssatz.
    - WICHTIG: Wenn eine Information (oder ein Key) nicht im Text vorhanden ist, darf dieser Key NICHT im JSON erscheinen. Erzeuge niemals "null", "" oder Platzhalter.
    - "Leere" Keys (z.B. "Key": "") oder Keys mit Platzhaltern sind streng verboten. Wenn kein Wert da ist: Key weglassen.

    JSON-STRUKTUR:
    {
      "extractedModel": "Hersteller + exakte Modellbezeichnung",
      "condensedSpec": { "Key": "Value" }
    }

    DETAIL-REGELN:
    1. extractedModel:
       - Nur ausgeben, wenn Hersteller und Modell zweifelsfrei im Text stehen. Sonst den Key komplett weglassen.
       - Beispiele:
            - Tech: "Lenovo ThinkPad X1 Carbon Gen 12"
            - Spielzeug: "Henrys Diabolo Kolibri"
            - Sport: "Nike Vapen X Boa"
    2. condensedSpec: Ein flaches Objekt (String-Map) der wichtigsten Merkmale (Maße, Farbe, Technik, Material).
       a) Werte:
          - Nur explizit genannte Werte.
          - Alle Werte sind Strings.
          - Einheiten normalisieren (z.B. "TB" statt "1000 GB", "GHz" statt "MHz").
          - Kein Zoll-Zeichen in Werten: schreibe "24 Zoll" statt "24\"" — ein rohes Anführungszeichen macht das JSON ungültig.
          - JEDER Eintrag in condensedSpec MUSS ein "Key": "Value"-Paar sein. Niemals ein bloßer String ohne zugehörigen Key (z.B. verboten: { "Material": "Holz", "Hart" } — "Hart" braucht einen Key, z.B. "Härtegrad": "Hart"). Findest du für einen Wert keinen passenden Key, lass den Wert weg statt ihn ohne Key einzufügen.
       b) Keys:
          - jeder Key darf nur einmal vorhanden sein.
          - Deutsch
          - Kurze Bezeichnungen, Wörter mit Leerzeichen getrennt
          - Erster Buchstabe groß (z.B. "Farbe", "Gewicht")
       c) Wenn keine Merkmale gefunden werden, lass den Key "condensedSpec" komplett weg
       """;

  public static final String PRODUCT_NAME_USER_DEFAULT = """
    Kategorie: {category}
    Titel: {title}

    Beschreibung:
    {description}
    """;

  // ─── QUICK_FACTS ─────────────────────────────────────────────────────────

  public static final String QUICK_FACTS_SYSTEM = """
    Du bist ein Daten-Analyst. Deine Aufgabe ist es, technische Spezifikationen aus verschiedenen Snippets von Produktseiten zu einer konsistenten Übersicht zusammenzuführen.

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
    1. Eindeutigkeit: Jeder Key darf GENAU EINMAL im JSON stehen — doppelte Keys sind kein gültiges JSON und führen zur Ablehnung der Antwort. Zusammenführen statt duplizieren.
    2. Formatierung Keys: Extrahiere physische und technische Eigenschaften (Maße, Gewicht, Auflösung, Anschlüsse, Material, etc.). Kurze deutsche Bezeichnungen, erster Buchstabe groß, Wörter mit Leerzeichen (z.B. "Prozessor", "Akku Kapazität"). Akronyme und Markennamen in bekannter Schreibweise (z.B. HDMI, USB, DisplayPort, OLED, Bluetooth). Keine Klammern oder Einheitenangaben im Key-Namen (falsch: "Bildschirmgröße (Zoll)"; richtig: "Bildschirmgröße").
    3. Formatierung Werte:
       - Alle Werte MÜSSEN flache Strings sein (keine Objekte, keine Arrays, keine reinen Zahlen).
       - Falsch: "Arbeitsspeicher": 16  |  Richtig: "Arbeitsspeicher": "16 GB"
       - Beispiel: "Display": "14 Zoll, 1920x1080" statt verschachtelter Objekte.
    4. Einheiten & Normalisierung: Verwende die größte sinnvolle Einheit (TB statt GB ab 1000 GB, GB statt MB ab 1000 MB, GHz statt MHz). Äquivalente Werte gelten als Duplikate.
    5. Spezialfall Erscheinungsjahr: Nur die 4-stellige Jahreszahl (z.B. "2023"). Keine Zeitspannen. Wenn unbekannt, Feld weglassen. Hinweis: "Baujahr" aus dem Inserat-Kontext entspricht dem Erscheinungsjahr — verwende stets "Erscheinungsjahr" als Feldname.
    6. Nur belegbare Werte: Wenn ein Wert nicht aus den Suchergebnissen erkennbar ist, lass das Feld weg (kein null, kein "unbekannt"). Erfinde keine Daten.
    7. Sources:
       - icecatId: Suche in den URLs nach Mustern wie '...-123456.html'. Extrahiere NUR die Ziffern am Ende vor der Dateiendung. Wenn keine Icecat-URL vorhanden ist, setze null.
       - sourceUrl: Die URL der Primärquelle, aus der die meisten Daten stammen.
    """;

  public static final String QUICK_FACTS_USER_DEFAULT = """
    Produkt: {lookupTerm}
    Kategorie: {category}
    {condensedSpec}
    Suchergebnisse:
    {snippets}
    """;

  // ─── HTML_FULL_SPECS ──────────────────────────────────────────────────────

  public static final String HTML_FULL_SPECS_SYSTEM = """
    Du extrahierst technische Spezifikationen aus einer Produktseite (vollständiger Seitentext oder Tabellen).
    Antworte NUR mit validem JSON — kein erklärender Text, keine Markdown-Backticks.
    Wenn ein Wert nicht erkennbar ist, lass das Feld weg (kein null, kein "unbekannt").
    Feldnamen im quickFacts-Objekt: Kleinbuchstaben, Englisch, keine Sonderzeichen.
    Gruppiere Felder nach ihren Abschnitten in featureGroups (z.B. "Display", "Connectivity").
    """;

  public static final String HTML_FULL_SPECS_USER_DEFAULT = """
    Produkt: {lookupTerm}
    Kategorie: {category}

    Seiteninhalt:
    {snippets}

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
    Map.entry(
      "Laptop / Notebook",
      List.of(
        new PromptConfig(
          PromptType.QUICK_FACTS,
          QUICK_FACTS_SYSTEM,
          """
          Produkt: {lookupTerm}
          Kategorie: Laptop / Notebook
          {condensedSpec}
          Suchergebnisse:
          {snippets}

          Relevante Felder für Laptops: Prozessor, Arbeitsspeicher, Speicher, Display, Akku, Gewicht, Betriebssystem
          """
        )
      )
    ),
    Map.entry(
      "Drucker & Scanner",
      List.of(
        new PromptConfig(
          PromptType.QUICK_FACTS,
          QUICK_FACTS_SYSTEM,
          """
          Produkt: {lookupTerm}
          Kategorie: Drucker & Scanner
          {condensedSpec}
          Suchergebnisse:
          {snippets}

          Relevante Felder für Drucker: Technologie, Farbe, Duplex, ADF, PPM Mono, PPM Farbe, Konnektivität
          """
        )
      )
    )
  );
}
