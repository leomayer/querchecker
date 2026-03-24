package at.querchecker.research.entity;

public enum ExtractionQuality {
    /** ≥60% SYSTEM-Pflichtfelder extrahiert + icecatId vorhanden (bei ICECAT-Quellen). */
    GOOD,
    /** <60% SYSTEM-Pflichtfelder oder icecatId fehlt bei ICECAT → nächste Quelle versuchen, Ergebnis merken. */
    PARTIAL,
    /** quickFacts leer → nächste Quelle versuchen, Ergebnis verwerfen. */
    EMPTY,
    /** Keine Pflichtfelder definiert + quickFacts leer → Lookup direkt als FAILED markieren. */
    FAILED_NO_CRITERIA
}
