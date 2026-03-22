package at.querchecker.research.entity;

public enum LookupStatus {
    COMPLETE,       // Specs gefunden (egal welche Stufe)
    FAILED,         // alle Stufen erfolglos
    QUOTA_EXCEEDED  // nicht gesucht, Kontingent erschöpft — überschrieben wenn Kontingent wieder frei
}
