package at.querchecker.research.entity;

public enum FieldSource {
    /**
     * Geseedet durch CategorySpecPreferenceSeeder. Generische Spec-Labels (cpu, ram, panel_type).
     * <p>
     * Werden als Pflichtfelder ans LLM übergeben und für die Extraktionsqualität bewertet
     * (Abdeckung ≥60% → GOOD).
     * <p>
     * Fließen NICHT in Brave-Queries ein: SYSTEM-Labels benennen Felder, keine Suchwerte —
     * "cpu" als Suchbegriff liefert keinen zusätzlichen Signal, da site:icecat.biz bereits
     * nach dem Produkt sucht. Nur USER-Keywords (Wert-Begriffe wie "OLED", "Thunderbolt 4")
     * verbessern die Trefferqualität in der Brave-Suche.
     */
    SYSTEM,

    /**
     * User-gepflegt via Settings. Wert-Keywords (oled, thunderbolt4, Core i7).
     * <p>
     * Fließen in Brave-Queries ein (≤5) und werden dem LLM als Kontext übergeben.
     * Werden NICHT für die Qualitätsbewertung herangezogen, da sie keine quickFacts-Keys
     * sind — das LLM extrahiert z.B. {@code panel_type: "OLED"}, nicht {@code oled: "..."}.
     */
    USER
}
