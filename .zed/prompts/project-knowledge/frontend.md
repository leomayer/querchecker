# Projektwissen: Querchecker — Frontend

## Angular 21+ Konventionen (verbindlich)

- **Standalone Components** — keine NgModules
- **Control Flow**: `@if`, `@for (x of list; track x.id)`, `@switch`
- **Signals**: `signal()`, `computed()`, `effect()`
- **Input/Output**: `input()` / `output()` — kein `@Input()` / `@Output()`
- **HTTP**: `httpResource()` statt HttpClient direkt
- **Change Detection**: **kein `ChangeDetectionStrategy.OnPush`** — Signals übernehmen die Granularität. Nicht importieren, nicht setzen.
- **Strict Mode** aktiv (`strict: true` in `tsconfig.json`)
- **Prettier** als Formatter (on-save)
- **Vitest** als Test-Runner

---

## Ordnerstruktur

```
frontend/src/app/
├── api/            ← generiert via openapi-generator-cli (nie manuell anfassen)
│   ├── model/      quercheckerListingDto, whListingDetailDto, whSearchResultDto, ...
│   └── api/        listings.service, listingDetail.service, ...
├── core/
│   ├── model/      icecat.model.ts (IcecatFeatureGroup, IcecatResponse, IcecatData)
│   │               ean-search.model.ts
│   ├── api-urls.ts ← hand-written, stable, nie überschreiben
│   ├── listing.service.ts
│   ├── dl-extraction.service.ts
│   ├── product-lookup.service.ts  ← lookup() + loadFullSpecs()
│   ├── icecat.service.ts          ← EAN-basierter Lookup (legacy, nicht importiert)
│   ├── ean-search.service.ts      ← EAN-Suche (legacy, nicht importiert)
│   ├── wh-meta.service.ts ← WhMetaService (root): categories + locations cached
│   ├── usage.service.ts, preferences.service.ts
│   └── health.service.ts, startup-overlay/, connection-banner/
├── features/
│   └── wh-search/
│       ├── main-layout/
│       ├── wh-filter/, location-filter/, category-filter/, wh-sort/
│       ├── wh-listings/ → listing-card/
│       ├── wh-detail/
│       │   ├── wh-base/
│       │   ├── item-annotation/
│       │   └── item-research/       ← Spec-Lookup, DL-Terms, Icecat-Accordion
│       │       ├── icecat-accordion/ ← Icecat Feature-Gruppen (Full-Specs API)
│       │       └── specs-accordion/  ← HTML-Fetch Feature-Gruppen (GSMArena/FlatpanelsHD)
│       ├── search.store.ts
│       ├── extraction.store.ts
│       ├── layout-state.enum.ts
│       └── listings.guard.ts
└── shared/
    ├── components/hierarchical-filter-component/, placeholder/
    │               gradient-progress-bar/ ← inputs: usage, limit. HSL-Farbverlauf (grün→gelb→rot). Label "X / Y"
    ├── layout/     app-header, app-footer, zone-left, zone-right
    └── pipes/      custom-currency
```

---

## SearchStore (`search.store.ts`, `@ngrx/signals`)

**State:**
- `layoutState: LayoutState` (SEARCH | LISTINGS | DETAIL)
- `listings: WhItemDto[]`
- `selectedId: string | null` — ID als String (aus URL-Segment)
- `searchQuery: SearchQuery | null`
- `loading`, `error`, `whTotal`
- `sortColumn`, `sortDirection`
- `searchPatches` — client-side optimistische Patches (Rating, viewCount)
- `filterDraft: { keyword, rows, priceFrom, priceTo, locationAreaId, categoryWhId, paylivery }` — Formular-State; beim `search()` nach localStorage gespeichert

**Computed:** `searchMode`, `patchedListings`

**Methoden:** `search()`, `selectListing()`, `backToListings()`, `clearSearch()`, `setFilterDraft()`, `setSortColumn/Direction()`, `setResourceState()`, `applySearchPatch()`, `removeListing()`, `advanceToNext()`

**Routing:** `backToListings()` → `router.navigate(['/listings'])` (explizit, kein `location.back()`). Settings-Backbutton → `location.back()`.

---

## Layout-States

| State | zone-left | zone-right |
|---|---|---|
| `SEARCH` | `app-wh-filter` | `app-placeholder` |
| `LISTINGS` | `app-wh-filter` | Sort + `app-wh-listings` |
| `DETAIL` | `app-wh-listings` | `app-wh-detail` |

`MainLayoutComponent` ist die einzige Route-Komponente. Förderband-Animationen für State-Übergänge.
Zwei `httpResource`: `allResource` (GET /api/listings?ratingFilter=UP_NULL) und `searchResource` (GET /api/wh/search).
State-Sync via `effect()` → `SearchStore.setResourceState()`.

---

## URL-Management (`core/api-urls.ts`) — nie überschreiben

Aktuelle Einträge (hand-written, nie überschreiben):
- `health`, `listings`, `whSearch`, `whLocations`, `whCategories`, `sse`
- `dlExtractionTerms(whItemId)` → `/api/dl/extraction/{id}/terms`
- `dlSettings` → `/api/dl/settings`
- `productSearch` → `/api/research/product/search`
- `icecatSpec(ean)` → `/api/research/icecat/{ean}`
- `productLookup(listingId)` → `/api/listings/{id}/lookup`
- `productFullSpecs(listingId)` → `/api/listings/{id}/lookup/full-specs`
- `apiUsage` → `/api/usage`
- `settingsPreferences` → `/api/settings/preferences`
- `settingsPreferenceCategory(categoryId)` → `/api/settings/preferences/{categoryId}`

Generierte Service-Klassen werden **nicht** für HTTP-Calls verwendet — nur DTO-Typen aus `api/model/`.

---

## ListingService (`core/listing.service.ts`)

`getDetail()`, `updateNote()`, `updateRating()`, `updateInterest()`, `updateTags()`, `recordView()`, `shouldRecordView()`, `delete()`
`lastViewed`-Map throttelt `recordView` auf 60s pro Inserat.

---

## Shared: `hierarchical-filter-component`

Generische N-stufige Baum-Navigation als `mat-autocomplete`.

```typescript
interface FilterNode {
  id: string;
  name: string;
  level: number;
  children?: FilterNode[];
  parentName?: string;
}
```

- **Inputs**: `data: FilterNode[]` (required), `label: string`, `selectedId?: string` — stellt Auswahl aus gespeicherter ID wieder her (reaktiv, wartet auf Datenladen)
- **Output**: `selectionChange: FilterNode | null`
- Klick auf Namen = Select; Klick auf `›` = Drill-down; `navStack` Signal für N-Ebenen
- `selectedPath` als farbige Chips dargestellt
- `highlight()` via `DomSanitizer.bypassSecurityTrustHtml()`
- `[displayWith]`, `[hideSingleSelectionIndicator]="true"`, `[autoActiveFirstOption]="true"`

**Chip-Farben** (`::ng-deep`): Beide Token-Präfixe setzen (`--mdc-chip-*` + `--mat-chip-*`).
`.mat-icon` innerhalb explizit überschreiben — globale `styles.scss`-Regel (`color: var(--color-on-surface-variant)`) macht Icons auf farbigen Chips sonst unsichtbar.

### `location-filter` / `category-filter`
Smart wrappers: konvertieren `FilterNode[]` aus `WhMetaService` (Root-Service, lädt Daten nur einmal).
- `locationAreaId = model<number | undefined>()` / `categoryWhId = model<number | undefined>()`
- Übergeben `[selectedId]="locationAreaId()?.toString()"` an `app-hierarchical-filter` für Restore nach Reload
- Location: numerisches Sort nach `areaId` (Bundesländer 1–8, Wien=900, Andere=22000)
- **`WhMetaService`** (`core/wh-meta.service.ts`, `providedIn: 'root'`): hält `categories` + `locations` als Signals. Notwendig, weil `MainLayoutComponent` bei jedem Routenwechsel neu erstellt wird — ohne diesen Service würden Chips nach jeder Navigation kurz verschwinden.

---

## Theme: TealMist — CSS Custom Properties

**Wichtig**: `--mat-sys-*` Tokens NICHT direkt verwenden. Immer projekteigene `--color-*` Variablen aus `styles.scss`!

| Variable | Light | Dark | Verwendung |
|---|---|---|---|
| `--color-haggle-primary` | `#184d5c` | `#003542` | **Nur** Header/Footer-Gradient — nie für UI-Elemente in dark |
| `--color-haggle-secondary` | `#346574` | `#184d5c` | **Nur** Header/Footer-Gradient + Chip Level 1 |
| `--color-ui-teal` | `#184d5c` | `#9dcee0` | UI-Elemente (Icons, Borders, Expand-Zones) — adaptiert automatisch |
| `--color-haggle-divider` | `#b9eafc` | `#9dcee0` | Diagonal-Akzent Header/Footer |
| `--color-on-haggle-primary` | `#ffffff` | `#ffffff` | Text auf Primär-Hintergrund |
| `--color-tertiary` | `#C8843A` | `#F0B97A` | Amber-Akzent — NUR für Hintergründe/Borders, **nicht als Textfarbe auf hellem Hintergrund** (Kontrast ~3:1) |
| `--color-positive` | `#1D6B52` | `#81C784` | Grün für positive Aktionen (thumb_up, positive Tags) — 5.7:1 / 5.5:1 |
| `--color-rating-down` | `#9E4C4C` | `#9E4C4C` | Rot für negative Bewertung — 5.56:1 |
| `--color-nav-action` | `#C8843A` | `#F0B97A` | FAB / Nav-Buttons Hintergrund |
| `--color-on-nav-action` | `#500808` | `#500808` | Icon-Farbe auf Nav-Action-Hintergrund |
| `--color-background` | `#fcf8f8` | `#131313` | Seiten-Hintergrund |
| `--color-surface` | `#fcf8f8` | `#131313` | Karten-Hintergrund |
| `--color-on-surface` | `#1c1b1b` | `#e5e2e1` | Primärer Text |
| `--color-on-surface-variant` | `#434749` | `#c4c7c9` | Sekundärer Text / Icons |
| `--color-border` | `rgba(0,0,0,0.14)` | `rgba(255,255,255,0.14)` | Rahmen |
| `--layout-gutter` | `24px` | — | Seitenabstand |

**Kontrast-Hinweise:**
- Amber `#C8843A` hat nur ~3:1 auf Weiß → nie als Textfarbe auf hellem Hintergrund. Als Hintergrund mit `--color-on-surface` als Text ist es korrekt.
- Für Darkened-Amber (Text/Icons auf hellem Hintergrund): `color-mix(in srgb, var(--color-tertiary) 65%, var(--color-on-surface))` → ~4.77:1
- Dark Mode hat kein Problem mit Amber (#F0B97A → 12.8:1 auf #131313)

Fonts: `--font-brand` (Dancing Script), `--font-body` (Source Sans 3)

---

## UI-Zonen-Layout

```
┌─────────────────────┐
│  1 – Header (fix)   │
├──────────┬──────────┤
│ 2 – Left │ 3 – Right│  beide overflow-y: auto
├──────────┴──────────┤
│  4 – Footer (fix)   │
└─────────────────────┘
```

Root: `flex-direction: column`, `height: 100vh`. Header/Footer: `flex: 0 0 auto`. Content-Row: `flex: 1 1 auto`.

---

## ExtractionStore (`extraction.store.ts`, `@ngrx/signals`)

Verwaltet DL-Extraktion, Spec-Lookup und Icecat-Daten clientseitig. Alle State-Slices per `whItemId` (= `WhItem.id`) geindext.

**State:**
- `results: Record<number, DlExtractionTermDto[]>` — DL-Extraktionsergebnisse
- `extractionStatus: Record<number, 'DONE'|'PENDING'|'CANCELLED'|'NONE'>`
- `suggestedTerms: Record<number, string>` — vorausgefüllter Suchbegriff (aus source-model)
- `lookupResults: Record<number, LookupResult>` — `{ lookupStatus, quickFacts, icecatId, sourceType, sourceDomain, sourceUrl, featureGroupsJson?, featureGroups, lookupTerm? }`
  - `featureGroups: SpecsFeatureGroup[]` — für HTML-Fetch-Quellen (GSMArena/FlatpanelsHD); null bei ICECAT/GENERIC
  - Store parst `featureGroupsJson` (Raw-JSON vom Backend) → `featureGroups` beim Speichern
- `lookupLoadingIds: number[]`, `fullSpecsLoadingIds: number[]`
- `fullSpecsLoaded: Record<number, boolean>`
- `fullSpecsData: Record<number, IcecatFeatureGroup[]>` — geparste Icecat-Feature-Gruppen

**Methoden:**
- `loadExistingTerms(whItemId)` — `GET /api/dl/extraction/{id}/terms` → aktualisiert results + extractionStatus + suggestedTerms
- `lookup(whItemId, listingId, lookupTerm)` — `POST /api/listings/{id}/lookup` → speichert LookupResult
- `loadFullSpecs(whItemId, listingId, icecatId)` — `POST /api/listings/{id}/lookup/full-specs` → parst `icecatSpecsJson` als `IcecatResponse`, speichert `FeaturesGroups` in `fullSpecsData`
- `remove(whItemId)` / `clear()` — bereinigt alle State-Slices inkl. `fullSpecsData`

**SSE:** `dl-extract` mergt Terms per Modell, setzt `extractionStatus = 'DONE'` und `suggestedTerms` (falls payload.suggestedTerm vorhanden und noch nicht gesetzt).

**`DlExtractionTermDto`**: `{ modelName, term, confidence, durationMs }`

---

## item-research Component (`wh-detail/item-research/`)

Vollständig implementiertes Feature:
- Suchfeld vorausgefüllt aus `suggestedTerms`; DL-Term-Chips klickbar zum Befüllen
- `auto_awesome` Button → `lookup()` → Spec-Lookup (Brave Search + LLM)
- **Quick-Facts-Tabelle** — geordnet: bevorzugte Felder (aus `preferredKeySet`) zuerst, Rest alphabetisch (`orderedQuickFacts`)
- **Quellenangabe** — "Daten von {domain} ↗" unter der Tabelle, verlinkt auf `sourceUrl` oder `https://{sourceDomain}` (`lookupSourceDomain` + `lookupSourceUrl` Signals)
- Geizhals-Link (öffnet Suche in neuem Tab)
- Icecat-Full-Specs-Button (`description` Icon) → `loadFullSpecs()` — nur wenn `showFullSpecsButton()` = true (= `icecatId != null && sourceType === 'ICECAT'`)
- **Icecat-Accordion** (`icecat-accordion/`) — Feature-Gruppen aus Icecat Full-Specs API
- **Specs-Accordion** (`specs-accordion/`) — Feature-Gruppen aus HTML-Fetch-Quellen (GSMArena, FlatpanelsHD); erscheint direkt nach Lookup, kein separater Button
- `noIcecatData`-Guard: kein quickFacts + keine icecatId + keine featureGroups
- `upc-search/` und `icecat-spec/` Sub-Komponenten entfernt (EAN/UPC-Feature gelöscht)

**Computed Signals:** `state`, `termGroups`, `lookupState`, `orderedQuickFacts`, `lookupIcecatId`, `showFullSpecsButton`, `lookupTerm`, `lookupSourceDomain`, `lookupSourceUrl`, `geizhalUrl`, `fullSpecsLoading`, `fullSpecsLoaded`, `icecatFeatureGroups`, `icecatGeneralInfo`, `specsFeatureGroups`, `noIcecatData`, `icecatPageUrl`, `icecatMismatch`

## Core Services

- `ProductLookupService` (`core/`): `lookup(listingId, lookupTerm)` + `loadFullSpecs(listingId, icecatId)`
- `IcecatService` (`core/`): `getByEan(ean)` — EAN-basierter Lookup (legacy, nicht importiert)
- `EanSearchService` (`core/`): Produktsuche → EAN-Ergebnisse (legacy, nicht importiert)
- `usage.service.ts`: `ProviderUsage { callsThisPeriod, callsToday, tokensIn, tokensOut, quotaUsage, quotaLimit }`. `ApiUsageResponse { brave, groq, openRouter }` — ICECAT entfernt. Warning-Threshold: ≥80% von `quotaLimit`.

## Health & Verbindungs-Handling

### `HealthService` (`core/health.service.ts`)
Signals: `backendReady()`, `connectionLost()`, `attempts()`, `serverRestartCount()`
- Pollt kontinuierlich: 2s Startup, **30s Idle** (gesund), **3s Rapid-Retry** (unterbrochen)
- `notifyServerError()` — cancelt geplanten Idle-Poll, pollt sofort; guard verhindert Doppel-Trigger
- `notifyServerRestart()` — inkrementiert `serverRestartCount` (von SSE bei Token-Mismatch aufgerufen)

### `StartupOverlayComponent` (`core/startup-overlay/`)
- Glassmorphisches Overlay: `backdrop-filter: blur(6px)` auf Overlay + `blur(24px)` auf Karte
- `AppComponent` rendert Header/Footer **immer**; `<router-outlet>` via `@if (health.backendReady())` gegatet
- Kein `@if/@else` mehr — App-Skeleton ist hinter dem Glas sichtbar

### `ConnectionBannerComponent` (`core/connection-banner/`)
- `@if (health.connectionLost())` in `MainLayoutComponent` oberhalb der Zones
- Zwei `mat-progress-bar` (oben + unten, untere via `scaleX(-1)` gespiegelt)

### `EventSourceServerService` (`shared/utils/event-source-server.ts`)
- `onerror` → `health.notifyServerError()` für sofortige Erkennung
- Effect: wenn `connectionLost` von `true → false` wechselt → `eventSource.close()` + `#connect()` sofort
- Token-Mismatch (Server-Neustart): Token still akzeptiert + `health.notifyServerRestart()` — **kein `window.location.reload()`**
- `#connect()` cleared immer zuerst `#stalenessTimer` (verhindert stale timer auf neuer Verbindung)

### `AppComponent`
- `effect()` auf `health.serverRestartCount()` → MatSnackBar "Server neugestartet — Verbindung wiederhergestellt" (5s)

---

## OpenAPI Workflow

`npm run generate-api` nach Backend-Änderungen → regeneriert `src/app/api/`.
Workflow: Backend lokal starten → generieren → committen.
Voraussetzung: `SpringDocConfig` Cycle-Breaker im Backend muss aktiv sein (sonst 500 beim Swagger-Abruf → NPE beim Generator).
