# Projektwissen: Querchecker

## Ziel
Angular-Web-App für Preisvergleiche (primär Elektronik/Hardware): Willhaben-Suchen durchführen, Ergebnisse mit anderen Plattformen (Geizhals etc.) cross-referenzieren, Marktpreisvergleich bieten.

---

## Projektname

**Querchecker** – eigenständiger Name, kein Markenkonflikt. Tool-Charakter („der Querchecker erledigt das für mich"), beschreibt das Kernfeature (Cross-Search zwischen Plattformen) direkt.

---

## Naming-Konventionen

### Abkürzungen
| Abkürzung | Bedeutung |
|---|---|
| `wh` | Willhaben |

### Allgemeine Regeln
- **Tabellen-ID** einer Entität ist immer `id` (PK, intern)
- **Fremdschlüssel** tragen den Entitätsnamen als Prefix: `whListingId`, nicht `listing` oder `id`
- **Externe IDs** (IDs fremder Plattformen) tragen den Plattform-Prefix: `whId` = Willhaben-interne ID
- **Entitäts-/Klassennamen** verwenden die Abkürzung: `WhListing`, `WhListingRepository`
- **DTOs** Richtung Angular tragen den Querchecker-Prefix: `QuercheckerListingDto`, `QuercheckerNoteDto`

---

## Monorepo-Struktur
```
querchecker/                ← Git-Root
├── backend/                ← Spring Boot Projekt (Maven)
├── frontend/               ← Angular Projekt
├── docker-compose.yml      ← Dev: nur PostgreSQL
├── docker-compose.prod.yml ← Prod: nginx + backend
└── README.md               ← Einzige Dokumentation
```

- Kein Tooling-Overhead (kein Nx, kein Turborepo) – plain Monorepo
- Zed öffnet den Root-Ordner als Workspace, erkennt beide Teilprojekte automatisch via jdtls (Java) und dem TypeScript-LSP (Angular)

---

## Stack

| Schicht | Technologie |
|---|---|
| Frontend | Angular 21, Angular Material V3 |
| Backend | Spring Boot (Java, Lombok, SpotBugs) |
| Datenbank | PostgreSQL via Docker (Dev) |
| API-Docs | springdoc-openapi (in Prod deaktiviert) |
| API-Codegen | openapi-generator-cli als devDependency in Angular |

---

## Features
- Suchmaske mit konfigurierbarer Zielplattform (Willhaben, Geizhals, …)
- Ergebnistabelle (Angular Material) mit lokalem Filtering
- Notizen je Ergebnis – gespeichert in PostgreSQL via `ListingNote`-Entity
- Per-Zeile Kreuzsuche auf anderen Plattformen
- Marktpreisvergleich (günstig/teuer-Einschätzung)

---

## Entwicklungsumgebung

**IDE:** Zed auf openSUSE Tumbleweed – öffnet Monorepo-Root als Workspace

### Keybindings (Eclipse-Keymap)
Eclipse-Keybindings importiert (`~/.config/zed/keymap.json`). Wichtiger Konflikt:
- `ctrl+shift+p` ist **belegt** (`editor::MoveToEnclosingBracket`, Eclipse: Jump to matching bracket) – öffnet **nicht** die Command Palette wenn der Editor fokussiert ist
- **Command Palette** stattdessen via `ctrl+3` (Eclipse: Ctrl+3)
- **Task spawnen**: `ctrl+3` → "task: spawn"
- **Letzten Task wiederholen**: `ctrl+f11` (Eclipse: Ctrl+F11)

---

## Offene TODOs
- [ ] Traefik-Konfiguration für Deployment finalisieren (Labels, Netzwerk, Domain)
- [ ] DTOs definieren und API-Client via openapi-generator-cli generieren
- [ ] Scraping-Strategie (Playwright angedacht, optional Claude API für Extraktion)
- [ ] Geizhals: keine öffentliche API, nur Community-Wrapper – Lösung offen
