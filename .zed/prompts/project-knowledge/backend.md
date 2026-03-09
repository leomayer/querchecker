# Projektwissen: Querchecker — Backend

## Package-Struktur

```
at.querchecker/
├── entity/         WhListing, WhListingDetail, WhCategory, WhLocation, AppConfig
├── dto/            QuercheckerListingDto, WhListingDetailDto, WhSearchResultDto,
│                   WhCategoryDto, WhLocationDto, WhMetaStatusDto
├── controller/     WhListingController, WhListingDetailController
├── service/        WhListingService, WhListingDetailService
├── repository/     WhListingRepository, WhListingDetailRepository,
│                   WhCategoryRepository, WhLocationRepository, AppConfigRepository
├── config/         CorsConfig, RestTemplateConfig
└── wh/             WhSearchController, WhSearchService, WhMetaController,
                    WhCategoryService, WhLocationService, WhRefreshScheduler,
                    api/WhApiResponse.java
```

---

## Entities (alle mit Lombok: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)

### `WhListing` — Kerndata vom Willhaben-Inserat
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `whId` | String (unique, Willhaben-interne ID) |
| `title` | String |
| `description` | String |
| `price` | BigDecimal |
| `location` | String |
| `url` | String |
| `thumbnailUrl` | String (nullable) |
| `listedAt` | LocalDateTime |
| `fetchedAt` | LocalDateTime |

### `WhListingDetail` — User-Annotationen (1:1 zu WhListing, lazy-created)
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `whListing` | ManyToOne → WhListing |
| `note` | String (nullable) |
| `viewCount` | Integer (default 0) |
| `lastViewedAt` | LocalDateTime (nullable) |
| `rating` | Enum UP/DOWN/null |
| `createdAt` | LocalDateTime |
| `updatedAt` | LocalDateTime |

### `WhCategory` — Hierarchischer Kategoriebaum (3 Ebenen)
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `whId` | String (Willhaben ATTRIBUTE_TREE ID) |
| `name` | String |
| `level` | Integer (0=root, 1=sub, 2=sub-sub) |
| `parent` | ManyToOne → WhCategory (nullable) |

### `WhLocation` — Hierarchischer Standortbaum
| Feld | Typ |
|---|---|
| `id` | Long (PK) |
| `areaId` | Integer (Willhaben areaId) |
| `name` | String |
| `level` | Integer (0=Bundesland, 1=Bezirk) |
| `parent` | ManyToOne → WhLocation (nullable) |

### `AppConfig` — Key-Value Konfiguration
| Feld | Typ |
|---|---|
| `key` | String (PK) |
| `value` | String |
| `description` | String |
| `updatedAt` | LocalDateTime |

---

## Repositories

- `WhListingRepository` – `findByWhId(String whId)`
- `WhListingDetailRepository` – `findByWhListingId(Long)`, `findAllSummaries()` (Projection für effiziente Joins)
  - Projection `WhListingDetailSummary`: `getListingId()`, `getNote()`, `getViewCount()`, `getLastViewedAt()`, `getRating()`
- `WhCategoryRepository`, `WhLocationRepository`, `AppConfigRepository` – Standard JpaRepository

---

## DTO-Mapping

Manuell via Builder im Service — kein Mapping-Framework (kein MapStruct).
- `QuercheckerListingDto` = WhListing-Felder + `hasNote`, `viewCount`, `lastViewedAt`, `rating`
- `WhListingDetailDto` spiegelt `WhListingDetail` vollständig

---

## API Endpoints

### Listings (`WhListingController`)
- `GET /api/listings` — alle Listings, opt. `ratingFilter` (UP | UP_NULL | DOWN | ALL)
- `GET /api/listings/{id}` — einzelnes Listing
- `POST /api/listings` — erstellen/speichern
- `DELETE /api/listings/{id}` — löschen

### Listing Detail (`WhListingDetailController`)
- `GET /api/listings/{id}/detail` — Detail-Metadaten
- `PUT /api/listings/{id}/detail/note` — Notiz speichern
- `PUT /api/listings/{id}/detail/rating` — Rating setzen (UP/DOWN/null)
- `POST /api/listings/{id}/views` — View-Event aufzeichnen

### Willhaben Search (`WhSearchController`)
- `GET /api/wh/search` — Params: `keyword`, `rows` (default 30), `priceFrom`, `priceTo`, `attributeTree`, `areaId`, `paylivery` (boolean)
- Gibt `WhSearchResultDto` zurück, speichert Ergebnisse in DB (upsert by `whId`)

### Willhaben Meta (`WhMetaController`)
- `GET /api/wh/meta/status` — `WhMetaStatusDto` (lastFetched, refreshInProgress, cron)
- `POST /api/wh/meta/refresh` — Async-Refresh triggern
- `GET /api/wh/meta/categories` — Kategoriebaum
- `GET /api/wh/meta/locations` — Standortbaum

Swagger UI: `/swagger-ui.html` (dev only, in Prod via `SPRING_PROFILES_ACTIVE=prod` deaktiviert)

---

## Willhaben-Integration (`wh/`)

- `WhApiResponse.java` — Mapping der inoffiziellen Willhaben JSON-API
- `WhSearchService` — URI bauen, Willhaben aufrufen, Listings upserten, DTOs zurückgeben
  - `buildThumbnailUrl()` — parst MMO-Attribut oder Fallback
  - `buildListingUrl()` — aus SEO_URL-Attribut
  - `upsertListing()` — merge-insert by `whId`
- `WhRefreshScheduler` — geplanter Metadata-Refresh (Kategorien, Standorte)

---

## Build & Tooling

- Lombok + SpotBugs (Maven-Plugin, läuft bei `mvn verify`)
- spring-boot-devtools: Hot-Restart nach `mvn compile`
- CORS: allows `http://localhost:14072`
- DB: `jdbc:postgresql://localhost:14071/mydb`, user `myuser`

---

## Erweiterungsstrategie (Multi-Provider)

Bei weiteren Quellen (Geizhals etc.) → `BaseListing`-Superklasse mit `@Inheritance`, `WhListing` als Subklasse. `WhListingDetail` bleibt unverändert, FK zeigt dann auf `BaseListing`.
