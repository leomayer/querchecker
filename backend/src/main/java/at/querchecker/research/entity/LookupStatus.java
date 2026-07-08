package at.querchecker.research.entity;

public enum LookupStatus {
    COMPLETE,       // Specs gefunden (egal welche Stufe)
    FAILED,         // Lookup versucht, kein Ergebnis — gecacht mit TTL
    QUOTA_EXCEEDED,     // Ebene-1: Provider-Freikontingent (Websuche) erschöpft — überschrieben wenn Kontingent wieder frei
    KEY_QUOTA_EXCEEDED, // Ebene-2: Tageskontingent des Access-Keys erschöpft — virtuell, nie in DB gespeichert
    NO_SOURCES,     // Kategorie nicht konfiguriert — virtuell, nie in DB gespeichert
    ERROR,          // technische Exception — gecacht mit kurzem TTL
    RATE_LIMITED    // Provider 429 — virtuell, nie in DB gespeichert; Retry kommt via SSE lookup-result
}
