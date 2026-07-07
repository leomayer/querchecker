package at.querchecker.auth;

public enum Role {
    USER,      // Kontingent = AccessKey.quotaLimit, DB entscheidet (Konzept Kap. 4)
    SUPERUSER  // kein Kontingent-Check, Settings-Spezialteile erlaubt
}
