package at.querchecker.research.entity;

public enum LookupStatus {
    COMPLETE,       // Specs gefunden (egal welche Stufe)
    FAILED,         // Lookup versucht, kein Ergebnis — gecacht mit TTL
    QUOTA_EXCEEDED, // nicht gesucht, Kontingent erschöpft — überschrieben wenn Kontingent wieder frei
    NO_SOURCES,     // Kategorie nicht konfiguriert — virtuell, nie in DB gespeichert
    ERROR           // technische Exception — gecacht mit kurzem TTL
}
