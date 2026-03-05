# Projektwissen: Querchecker

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
│  flex-row container             │
│  ┌──────────┐ ┌───────────────┐ │ Zone 2 and Zone 3 are 
│  │ Zone 2   │ │ Zone 3        │ │ scrollable
│  │ overflow │ │ overflow      │ │
│  │ -y: auto │ │ -y: auto      │ │
│  │ (filter) │ │ (results)     │ │
│  └──────────┘ └───────────────┘ │
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

## Theme: „TealMist" — CSS Custom Properties

**Wichtig**: `--mat-sys-*` Tokens sind NICHT als CSS Custom Properties verfügbar. Immer die projekteigenen `--color-*` Variablen verwenden!

| Variable | Wert | Verwendung |
|---|---|---|
| `--color-haggle-primary` | `#4a7a8a` | Primary-Farbe, Icons, Akzente |
| `--color-haggle-secondary` | `#5a8f8f` | Secondary-Farbe |
| `--color-on-haggle-primary` | `#ffffff` | Text auf Primary-Hintergrund |
| `--color-on-surface-variant` | `#5a8f8f` | Sekundärer Text, Icons |
| `--color-border` | `rgba(0,0,0,0.14)` | Trennlinien, Borders |
| `--color-surface` | `#ffffff` | Karten-Hintergrund |
| `--color-background` | `#f5f5f5` | Seiten-Hintergrund |
| `--layout-gutter` | `24px` | Horizontaler Innenabstand |

**Globale Icon-Farbe**: `styles.scss` setzt `.mat-icon { color: var(--color-on-surface-variant) }`.
Abweichung in Komponenten: `mat-icon { color: var(--color-haggle-primary) }` explizit setzen.

Fonts: `--font-brand` (Dancing Script), `--font-body` (Source Sans 3)

---

## Shared Components

### `hierarchical-filter-component` (`shared/components/hierarchical-filter-component/`)
Generische N-stufige Baum-Navigation als `mat-autocomplete`.

```typescript
// FilterNode Model
interface FilterNode {
  id: string;       // String-ID (z.B. areaId als String)
  name: string;
  level: number;
  children?: FilterNode[];
  parentName?: string;
}
```

- **Inputs**: `data: FilterNode[]` (required), `label: string`
- **Output**: `selectionChange: FilterNode | null`
- **UX**: Klick auf Namen = Select; Klick auf `›`-Button (expand-zone) = Drill-down in Kinder
- **navStack**: Signal-Stack für N-Ebenen-Navigation; goBack() popt
- **Mat-Autocomplete Besonderheiten**:
  - `[displayWith]="displayFn"` → verhindert `[object Object]` nach Selection
  - `[hideSingleSelectionIndicator]="true"` → eigenes Check-Icon links statt Material-Trailing-Indicator
  - `[autoActiveFirstOption]="true"` → Enter wählt ersten Eintrag direkt
- **Highlight**: `highlight(text, query)` via `DomSanitizer.bypassSecurityTrustHtml()` für Bold-Treffer

### `location-filter` (`features/wh-search/location-filter/`)
Smart Component: fetcht `WhLocationDto[]`, konvertiert zu `FilterNode[]`, übergibst an `hierarchical-filter`.

- Willhaben areaIds: Bundesländer 1–8, Wien = 900, Andere Länder = 22000
- Sortierung: numerisch nach `areaId` — Andere Länder landet dadurch automatisch am Ende
- Interface nach außen: `locationAreaId = model<number | undefined>()`


### Ports (lokal)

| Port | Service |
|---|---|
| `14050` | Paperless (lokal, zum Testen) |
| `14070` | Spring Boot Backend |
| `14071` | PostgreSQL (Docker, `14071:5432`) |
| `14072` | Angular (ng serve) |

- DB-Verwaltung: DBeaver Community
- Paperless läuft isoliert via Docker auf `14050` (kein Konflikt)

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
