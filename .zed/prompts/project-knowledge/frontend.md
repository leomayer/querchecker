# Projektwissen: Querchecker — Frontend

## Angular 21+ Konventionen (verbindlich)

- **Standalone Components** — keine NgModules
- **Control Flow**: `@if`, `@for (x of list; track x.id)`, `@switch`
- **Signals**: `signal()`, `computed()`, `effect()`
- **Input/Output**: `input()` / `output()` — kein `@Input()` / `@Output()`
- **HTTP**: `httpResource()` statt HttpClient direkt
- **Strict Mode** aktiv (`strict: true` in `tsconfig.json`)
- **Prettier** als Formatter (on-save)
- **Vitest** als Test-Runner

---

## Ordnerstruktur

```
frontend/src/app/
├── api/            ← generiert via openapi-generator-cli (nie manuell anfassen)
│   ├── model/      quercheckerListingDto, whListingDetailDto, whSearchResultDto,
│   │               whCategoryDto, whLocationDto, whMetaStatusDto
│   └── api/        listings.service, listingDetail.service, ...
├── core/
│   ├── api-urls.ts ← hand-written, stable, nie überschreiben
│   └── listing.service.ts
├── features/
│   └── wh-search/
│       ├── main-layout/       ← Einzige Route-Komponente (Förderband)
│       ├── wh-filter/         ← Suchformular (keyword, rows, price, paylivery)
│       ├── location-filter/   ← smart wrapper um hierarchical-filter
│       ├── category-filter/   ← smart wrapper um hierarchical-filter
│       ├── wh-listings/       ← Ergebnisliste mit Rating-Filter-Tabs
│       │   └── listing-card/  ← Karte mit Thumbnail, Rating-Buttons, Stats
│       ├── wh-sort/           ← Sortierung
│       ├── wh-detail/         ← Detail-Panel (rechte Zone, kein Dialog)
│       ├── search.store.ts    ← @ngrx/signals SignalStore
│       ├── layout-state.enum.ts ← SEARCH | LISTINGS | DETAIL
│       ├── search-query.model.ts
│       └── listings.guard.ts
└── shared/
    ├── components/
    │   ├── hierarchical-filter-component/
    │   └── placeholder/
    ├── layout/     app-header, app-footer, zone-left, zone-right
    └── pipes/      custom-currency
```

---

## SearchStore (`search.store.ts`, `@ngrx/signals`)

**State:**
- `layoutState: LayoutState` (SEARCH | LISTINGS | DETAIL)
- `listings: QuercheckerListingDto[]`
- `selectedId: number | null`
- `searchQuery: SearchQuery | null`
- `loading`, `error`, `whTotal`
- `sort: { column, direction }`
- `searchPatches` — client-side optimistische Patches (z.B. Rating)

**Computed:** `searchMode`, `patchedListings`

**Methoden:** `search()`, `selectListing()`, `backToListings()`, `clearSearch()`, `setSortColumn/Direction()`, `setResourceState()`, `applySearchPatch()`, `advanceToNext()`

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

```typescript
export const API_URLS = {
  listings: '/api/listings',
  listingById: (id: number) => `/api/listings/${id}`,
  whSearch: '/api/wh/search',
  whLocations: '/api/wh/meta/locations',
  whCategories: '/api/wh/meta/categories',
} as const;
```

Generierte Service-Klassen werden **nicht** für HTTP-Calls verwendet — nur DTO-Typen aus `api/model/`.

---

## ListingService (`core/listing.service.ts`)

`getDetail()`, `updateNote()`, `updateRating()`, `recordView()`, `delete()`
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

- **Inputs**: `data: FilterNode[]` (required), `label: string`
- **Output**: `selectionChange: FilterNode | null`
- Klick auf Namen = Select; Klick auf `›` = Drill-down; `navStack` Signal für N-Ebenen
- `selectedPath` als farbige Chips dargestellt
- `highlight()` via `DomSanitizer.bypassSecurityTrustHtml()`
- `[displayWith]`, `[hideSingleSelectionIndicator]="true"`, `[autoActiveFirstOption]="true"`

**Chip-Farben** (`::ng-deep`): Beide Token-Präfixe setzen (`--mdc-chip-*` + `--mat-chip-*`).
`.mat-icon` innerhalb explizit überschreiben — globale `styles.scss`-Regel (`color: var(--color-on-surface-variant)`) macht Icons auf farbigen Chips sonst unsichtbar.

### `location-filter` / `category-filter`
Smart wrappers: fetchen `/api/wh/meta/locations` bzw. `/api/wh/meta/categories`, konvertieren zu `FilterNode[]`.
- `locationAreaId = model<number | undefined>()` / `categoryWhId = model<string | undefined>()`
- Location: numerisches Sort nach `areaId` (Bundesländer 1–8, Wien=900, Andere=22000)

---

## Theme: TealMist — CSS Custom Properties

**Wichtig**: `--mat-sys-*` Tokens NICHT verwenden. Immer projekteigene `--color-*` Variablen!

| Variable | Wert |
|---|---|
| `--color-haggle-primary` | `#4a7a8a` |
| `--color-haggle-secondary` | `#5a8f8f` |
| `--color-on-haggle-primary` | `#ffffff` |
| `--color-on-surface-variant` | `#5a8f8f` |
| `--color-border` | `rgba(0,0,0,0.14)` |
| `--color-surface` | `#ffffff` |
| `--color-background` | `#f5f5f5` |
| `--layout-gutter` | `24px` |

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

## OpenAPI Workflow

`npm run generate-api` nach Backend-Änderungen → regeneriert `src/app/api/`.
Workflow: Backend lokal starten → generieren → committen.
