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

## Datenmodell

Zwei JPA-Entities, beide mit Lombok (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`).

### `WhListing`
Speichert Suchergebnisse von Willhaben:

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | Long | PK, auto-generated |
| `whId` | String | Willhaben-interne ID (stabiler Anker) |
| `title` | String | Titel des Inserats |
| `description` | String | Beschreibung |
| `price` | BigDecimal | Preis |
| `location` | String | Standort |
| `url` | String | Link zum Inserat |
| `listedAt` | LocalDateTime | Einstelldatum auf Willhaben |
| `fetchedAt` | LocalDateTime | Zeitpunkt des Abrufs |

### `ListingNote`
Speichert Notizen zu einem Listing:

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | Long | PK, auto-generated |
| `whListingId` | ManyToOne → WhListing | Zugehöriges Inserat |
| `content` | String | Notizinhalt |
| `createdAt` | LocalDateTime | Erstellzeitpunkt |
| `updatedAt` | LocalDateTime | Letztes Update |

Erweiterbar um z.B. `category`, `tags`, `rating`.

### Repositories
- `WhListingRepository` – `findByWhId(String whId)`
- `ListingNoteRepository` – `findByWhListingId(Long whListingId)`

### Erweiterungsstrategie
Bei weiteren Quellen (Geizhals etc.) → `BaseListing`-Superklasse mit `@Inheritance`, `WhListing` und künftige Entities als Subklassen. `ListingNote` bleibt unverändert, FK zeigt dann auf `BaseListing`.

---

## OpenAPI / Codegen-Workflow
- Spring Boot exponiert OpenAPI-Spec via springdoc
- Angular generiert TypeScript-Client: `npm run generate-api`
- Generierte Files in `frontend/src/app/api/` committen (Option B)
- Workflow bei DTO-Änderungen: Backend lokal starten → generieren → committen
- In Produktion springdoc deaktiviert via `SPRING_PROFILES_ACTIVE=prod`

### DTOs
- `QuercheckerListingDto` – spiegelt `WhListing` (ohne JPA-interne Felder)
- `QuercheckerNoteDto` – spiegelt `ListingNote`
- Mapping zwischen Entity und DTO im Service (manuell via Builder, kein Mapping-Framework)

### Rolle des generierten Codes im Frontend
Die generierten Service-Klassen (z.B. `WhListingService`) werden **nicht direkt verwendet**.
`httpResource()` übernimmt den HTTP-Layer signal-nativ. Der generierte Code liefert ausschließlich:

| Zweck | Verwendet |
|---|---|
| DTO-Typdefinitionen (`QuercheckerListingDto` etc.) | ✅ Ja |
| Generierte Service-Klassen (HTTP-Calls) | ❌ Nicht verwendet |

Die generierten Service-Klassen parallel zu `httpResource()` zu nutzen würde `switchMap`-Wrapper erfordern, die gegen signal-native Reaktivität arbeiten.

---

## Backend (Spring Boot)

- Port `14070`, läuft direkt via JDK
- Rolle: CORS-Proxy zur inoffiziellen Willhaben-JSON-API
- Lombok + SpotBugs (als Maven-Plugin, läuft bei `mvn verify`)
- Datasource: `jdbc:postgresql://localhost:14071/mydb`

### application.yml (Grundkonfiguration)
```yaml
server:
  port: 14070

spring:
  datasource:
    url: jdbc:postgresql://localhost:14071/mydb
    username: myuser
    password: mypassword
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

springdoc:
  api-docs:
    enabled: true
```

### application-prod.yml
```yaml
springdoc:
  api-docs:
    enabled: false
```

### CORS-Konfiguration
Angular Dev-Server läuft auf Port `14072`, daher muss Spring Boot CORS für diesen Origin erlauben:
```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:14072")
                        .allowedMethods("GET", "POST", "PUT", "DELETE");
            }
        };
    }
}
```

### SpotBugs (pom.xml)
Läuft nicht als Live-LSP in Zed, sondern als Maven-Plugin bei `mvn verify` – meldet echte Bugs (Null-Dereferenzen, fehlerhafte Equals-Implementierungen etc.), kein akademischer Style-Overhead:
```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.3.1</version>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

---

## Angular

- Port `14072`, läuft via `ng serve`
- Proxy: `frontend/proxy.conf.json` → alle `/api`-Requests an `http://localhost:14070`
- Angular Material V3, Theme in `frontend/src/custom-theme.scss`
- Dark Theme via `.dark-theme`-Klasse am `<body>`
- `angular.json` styles: `custom-theme.scss` + `styles.scss`
- Fonts: *Dancing Script* (Brand) + *Source Sans 3* (UI) via Google Fonts
- **Strict Mode** aktiv (`strict: true` in `tsconfig.json`) – Standard in Angular 20+
- **Prettier** als Formatter, läuft on-save in Zed
- **Vitest** als Test-Runner (Angular 21 Standard, ersetzt Karma)

### proxy.conf.json
```json
{
  "/api": {
    "target": "http://localhost:14070",
    "secure": false,
    "changeOrigin": true
  }
}
```

In `angular.json` einbinden:
```json
"serve": {
  "options": {
    "proxyConfig": "proxy.conf.json"
  }
}
```

### Prettier (Zed-Integration)
Prettier in `frontend/` installieren und Zed so konfigurieren, dass es on-save formatiert:
```bash
npm install --save-dev prettier
```

Zed `settings.json`:
```json
{
  "languages": {
    "TypeScript": {
      "formatter": {
        "external": {
          "command": "prettier",
          "arguments": ["--stdin-filepath", "{buffer_path}"]
        }
      },
      "format_on_save": "on"
    }
  }
}
```

### Angular 21+ Konventionen
- **Standalone Components** als Standard – keine `NgModules`
- **Neue Control Flow Syntax** statt Direktiven:
```html
  @if (listings().length > 0) { ... }
  @for (listing of listings(); track listing.id) { ... }
```
- **Signals** für reaktiven State:
```typescript
  listings = signal<QuercheckerListingDto[]>([]);
  count = computed(() => this.listings().length);
```
- `effect()` für Seiteneffekte die auf Signal-Änderungen reagieren
- `input()` / `output()` statt `@Input()` / `@Output()` Decorators

### Frontend-Ordnerstruktur

```
frontend/src/app/
├── api/        ← generiert via openapi-generator-cli, nie manuell anfassen
├── core/
│   └── api-urls.ts   ← hand-written, stabil, nicht überschreibbar
├── features/   ← Feature-Komponenten (Listings, Cross-Search etc.)
└── shared/     ← Wiederverwendbare Komponenten/Pipes
```

### URL-Management (`api-urls.ts`)

Backend-URLs werden **nicht** aus dem generierten Code extrahiert (der Generator vergräbt sie in Service-Methoden – fragil, geht bei Regenerierung verloren).

Stattdessen: eine einzige hand-geschriebene Konstanten-Datei in `core/`:

```typescript
// Hand-written — do NOT delete or move to api/.
// Generated API services provide types only; URLs are owned here.
export const API_URLS = {
  whListings: '/api/wh/listings',
  whListingById: (id: number) => `/api/wh/listings/${id}`,
} as const;
```

Bei Backend-Routen-Änderung → nur `api-urls.ts` anpassen.

### App-Titel / Toolbar
- `index.html`: `<title>Querchecker</title>`
- Toolbar zeigt ausschließlich den Markentitel – kein zusätzliches Navigations-Element
- Brand-Styling in `styles.scss`:
```scss
.brand-title {
  font-family: var(--font-brand); // 'Dancing Script'
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--color-on-primary);
}
```

- Toolbar-Template in `app.component.html`:
```html
<mat-toolbar color="primary">
  <span class="brand-title">Querchecker</span>
</mat-toolbar>
```

---

## UI Design Prinzipien

### Layout-Shell (4 Zonen + Hauptbereich)

```
┌─────────────────────────────────┐
│         1 – Header              │  fix, always visible
├──────────────┬──────────────────┤
│  2 – Left    │   3 – Right      │  Right: scrollable
│  (fix)       │   (results)      │
├──────────────┴──────────────────┤
│         4 – Footer              │  fix, always visible
└─────────────────────────────────┘
```

| Zone | Inhalt |
|---|---|
| 1 – Header | Toolbar mit Querchecker Brand |
| 2 – Left | Suchmaske / Filterformular (Willhaben) – vorerst spärlich, scrollt unabhängig |
| 3 – Right | Scrollbare Ergebnisliste |
| 4 – Footer | Statuszeile / Meta-Info |

- **Root-Layout** (`app-root`) ist ein `flex-direction: column` Container mit `height: 100vh`
- Header und Footer: `flex: 0 0 auto` – nehmen nur ihren eigenen Platzbedarf
- Content-Row (Zone 2 + Zone 3): `flex: 1 1 auto` – füllt exakt den verbleibenden Raum automatisch
- Keine hardcodierten Höhen, keine Berechnungen – ändert sich die Header-Höhe, passen sich Zone 2 und 3 automatisch an
- Zone 2 und Zone 3 sind eigenständige Flex-Container innerhalb der Content-Row, jeweils mit `height: 100%` und `overflow-y: auto` → unabhängige Scroll-Kontexte
- **Mobile First** – Layout passt sich via Flexbox natürlich an, ohne schwere Media-Query-Logik

```css
body, app-root {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.header  { flex: 0 0 auto; }  /* nimmt nur eigenen Platzbedarf */
.footer  { flex: 0 0 auto; }  /* nimmt nur eigenen Platzbedarf */
.content { flex: 1 1 auto; }  /* füllt exakt den Rest */

.content {
  display: flex;
  flex-direction: row;
}

.zone-2, .zone-3 {
  height: 100%;
  overflow-y: auto;  /* unabhängige Scroll-Kontexte */
}
```

### Component Scrollability
- Zone 3 (Right) ist der scrollbare Inhaltsbereich – `overflow-y: auto` am Results-Container innerhalb der Flex-Shell
- Zonen 1, 2 und 4 sind fix und scrollen nicht mit dem Inhalt

### Dual Color Tone
Das TealMist Primary/Secondary-Split ist ein **strukturelles visuelles Muster**, nicht nur Theming. Zusammengehörige Zonenpärchen (Header↔Footer, Left↔Right) spiegeln den Zwei-Ton bewusst wider – gilt für alle Komponenten.

### Filter-Signale & httpResource

Zwei Trigger-Typen im Left-Panel:

| Typ | Beispiele | Verhalten |
|---|---|---|
| **Sofort** | Location, Kategorie, Gratis-Toggle | Signal-Änderung → `httpResource` feuert direkt |
| **Debounced** | Freitext-Eingabe | Signal-Änderung → ~  300ms Delay → `httpResource` feuert |

Kein Such-Button nötig – Debounce auf Texteingabe macht die UX reaktiv ohne Server-Spam.

```typescript
readonly filterQuery = signal('');
readonly location = signal('');
readonly gratis = signal(false);

readonly listings = httpResource(() => ({
  url: API_URLS.whListings,
  params: {
    query: this.filterQuery(),
    location: this.location(),
    gratis: this.gratis()
  }
}));
```

`httpResource()` re-fetched automatisch bei jeder Signal-Änderung. Angular 21+ macht dies zum kanonischen Ansatz.

### State Management
- **Kein NgRx, kein SignalStore** – wird zum jetzigen Zeitpunkt nicht benötigt
- Filter-Signale leben flach in der Komponente oder einem minimalen Service
- Bei wachsender Komplexität ist die Service-Schicht der natürliche Extraktionspunkt

### Plattform-Abstraktion (Willhaben-first)
- Willhaben ist der erste und aktuell einzige Provider
- Cross-Search / Detail-Ansicht (z.B. Geizhals): Platzierung noch offen — Überlegung ist, dass  Details in Zone 3 angezeigt werden, die Übersicht in Zone 2
- Provider-spezifische Logik bleibt isoliert, damit Willhaben später ersetzt oder ergänzt werden kann ohne strukturelle UI-Änderungen
- Spiegelt die Backend-Strategie (`BaseListing` / `@Inheritance`) auf der Frontend-Seite wider

---

## Theme: „TealMist"

| Token | Wert |
|---|---|
| Primary | `#4a7a8a` |
| Secondary | `#5a8f8f` |
| Tertiary | `#7ab0b0` |
| Background | `#f5f5f5` |
| Surface | `#ffffff` |
| Border | `#7ab0b0` |

CSS Custom Properties: `--color-primary`, `--color-secondary`, `--color-tertiary`, `--color-background`, `--color-surface`, `--color-on-primary`, `--color-border`, `--font-brand`, `--font-body`

---

## Entwicklungsumgebung

**IDE:** Zed auf openSUSE Tumbleweed – öffnet Monorepo-Root als Workspace

### Keybindings (Eclipse-Keymap)
Eclipse-Keybindings importiert (`~/.config/zed/keymap.json`). Wichtiger Konflikt:
- `ctrl+shift+p` ist **belegt** (`editor::MoveToEnclosingBracket`, Eclipse: Jump to matching bracket) – öffnet **nicht** die Command Palette wenn der Editor fokussiert ist
- **Command Palette** stattdessen via `ctrl+3` (Eclipse: Ctrl+3)
- **Task spawnen**: `ctrl+3` → "task: spawn"
- **Letzten Task wiederholen**: `ctrl+f11` (Eclipse: Ctrl+F11)

### Ports (lokal)

| Port | Service |
|---|---|
| `14050` | Paperless (lokal, zum Testen) |
| `14070` | Spring Boot Backend |
| `14071` | PostgreSQL (Docker, `14071:5432`) |
| `14072` | Angular (ng serve) |

- DB-Verwaltung: DBeaver Community
- Paperless läuft isoliert via Docker auf `14050` (kein Konflikt)

### docker-compose.yml (Dev)
Nur PostgreSQL:
```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_USER: myuser
      POSTGRES_PASSWORD: mypassword
      POSTGRES_DB: mydb
    ports:
      - "14071:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

---

## Deployment (Homeserver)

### Strategie: Option B – zwei Container + Nginx + Traefik
```
Internet → Traefik → querchecker.domain.at → Nginx-Container → Spring Boot-Container
                   → paperless.domain.at → Paperless
                   → ...
```

- **Nginx-Container** serviert Angular-Build-Output
- **Spring Boot-Container** nur intern erreichbar
- **Traefik** läuft bereits am Homeserver (für Paperless) – diese App hängt sich ins selbe Docker-Netzwerk ein
- SSL via Let's Encrypt wird von Traefik übernommen
- Kein Eingriff in bestehende Paperless-Konfiguration nötig

### docker-compose.prod.yml (Grundstruktur)
```yaml
services:
  querchecker-nginx:
    image: querchecker-frontend
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.querchecker.rule=Host(`querchecker.deinedomain.at`)"
      - "traefik.http.routers.querchecker.tls.certresolver=letsencrypt"
    networks:
      - traefik-network

  querchecker-backend:
    image: querchecker-backend
    networks:
      - traefik-network
      - internal

networks:
  traefik-network:
    external: true
  internal:
    driver: bridge
```

### Build-Ablauf
1. Angular Build (Multi-Stage Dockerfile in `frontend/`)
2. Spring Boot Build via Maven (Multi-Stage Dockerfile in `backend/`)
3. `docker compose -f docker-compose.prod.yml up -d` am Homeserver

---

## Features
- Suchmaske mit konfigurierbarer Zielplattform (Willhaben, Geizhals, …)
- Ergebnistabelle (Angular Material) mit lokalem Filtering
- Notizen je Ergebnis – gespeichert in PostgreSQL via `ListingNote`-Entity
- Per-Zeile Kreuzsuche auf anderen Plattformen
- Marktpreisvergleich (günstig/teuer-Einschätzung)

---

## Offene TODOs
- [ ] Traefik-Konfiguration für Deployment finalisieren (Labels, Netzwerk, Domain)
- [ ] DTOs definieren und API-Client via openapi-generator-cli generieren
- [ ] Scraping-Strategie (Playwright angedacht, optional Claude API für Extraktion)
- [ ] Geizhals: keine öffentliche API, nur Community-Wrapper – Lösung offen
