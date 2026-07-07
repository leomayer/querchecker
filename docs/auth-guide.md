# Querchecker Auth Guide

Zugriffscodes, Rollen und Zugriffsverwaltung — für Nutzer und Administratoren.

> **Status: Entwurf.** Rollen, Login/Logout, Zugriffsverwaltung (Generieren/Bearbeiten/Sperren/Löschen) und GUEST-Gating der KI-Funktionen sind fertig. Noch offen: die tatsächliche Tageskontingent-**Zählung** (P4) — `quotaLimit` ist am Key hinterlegt, wird aber noch nicht gegen echten Verbrauch geprüft. Details/Fortschritt: [Konzept-Doku](auth/berechtigungen-konzept.md), Kap. 8. Diese Seite wird erst final in die Haupt-Doku (README/Admin Guide) gemergt, wenn P4 steht.

---

## Wie Zugriff funktioniert

Querchecker kennt drei Zustände:

| Zustand | Was du siehst | KI-Funktionen |
| --- | --- | --- |
| **Gast** (kein Zugriffscode) | Normale Suche, alle Basis-Funktionen | ❌ Ausgeblendet |
| **User** (Zugriffscode eingegeben) | Wie Gast + eigenes Tageskontingent | ✅ Spec-Lookup, Produktanalyse |
| **Superuser** | Wie User, ohne Kontingent-Limit | ✅ + Zugriffsverwaltung, Usage-Monitor, Provider-Einstellungen |

Ohne Zugriffscode ist Querchecker voll nutzbar — nur die KI-gestützten Funktionen (automatische Produkterkennung, Spezifikations-Lookup) bleiben Nutzern mit Code vorbehalten, da sie externe API-Kontingente verbrauchen.

---

## Als Nutzer: Zugriffscode eingeben

1. **Einstellungen** öffnen
2. Zugriffscode-Feld ausfüllen, **Anmelden** klicken
3. Die Anmeldung gilt für den Browser (Cookie), kein erneutes Eingeben nötig — läuft nach 30 Tagen Inaktivität ab
4. **Abmelden** über denselben Bereich in den Einstellungen

Zugriffscodes gibt der Administrator aus (siehe unten). Ein Code ist einmalig sichtbar — verloren gegangene Codes können nicht wiederhergestellt werden, nur ein neuer Code ausgestellt.

---

## Als Superuser: Zugriffsverwaltung

Sichtbar in **Einstellungen → Zugriffsverwaltung** (nur für Superuser-Konten).

### Neuen Zugriffscode erstellen

1. **+ Neuen Key** klicken
2. Rolle wählen: `USER` (mit Tageskontingent) oder `SUPERUSER` (kein Limit)
3. Bei `USER`: Tageskontingent festlegen (Vorschlag: 10)
4. Bestätigen — der Klartext-Code wird **einmalig** angezeigt. Sofort kopieren und weitergeben — er lässt sich später nicht mehr abrufen.

### Codes verwalten

Tabelle zeigt alle Codes mit Rolle, Kontingent, Erstellungsdatum, letzter Nutzung und Status:

- **Sperren** — Code funktioniert sofort nicht mehr (auch laufende Sitzungen werden beendet)
- **Entsperren** — Code wieder aktivieren
- **Löschen** — Code endgültig entfernen
- **Bearbeiten** — Rolle oder Kontingent ändern

Dein eigener aktiver Code ist mit **„Du"** markiert und lässt sich nicht bearbeiten, sperren oder löschen — Schutz vor versehentlichem Selbst-Aussperren.

### Wichtig

- Es gibt keine Codes-Wiederherstellung — nur Neuausstellung
- Gesperrte Codes bleiben in der Liste sichtbar (Historie), zählen aber nicht mehr
- Das Tageskontingent ist am Code hinterlegt, wird aber noch **nicht gegen echten Verbrauch geprüft** — das kommt erst mit P4

---

## Bootstrap: ersten Superuser-Zugriff einrichten

Bei einer frischen Installation gibt es noch keinen Zugriffscode. Erster Zugang nur per direktem Datenbank-Eintrag:

1. Zufälligen Code wählen, z.B. `openssl rand -hex 32`
2. Hash bilden: `echo -n "<code>" | sha256sum`
3. In der Datenbank einfügen:
   ```sql
   INSERT INTO access_key (secret_key_hash, role, quota_limit, used, revoked)
   VALUES ('<sha256-hex>', 'SUPERUSER', 0, false, false);
   ```
4. Mit dem gewählten Klartext-Code normal über die Einstellungen anmelden — danach weitere Codes bequem über die Zugriffsverwaltung ausstellen

---

## See Also

- 📖 [Admin Guide](admin-guide.md) — Installation, Konfiguration und Betrieb
- 🏗️ [Konzept & technische Details](auth/berechtigungen-konzept.md) — Rollenmodell, Session-Mechanik, offene Punkte (Entwickler-Doku)
