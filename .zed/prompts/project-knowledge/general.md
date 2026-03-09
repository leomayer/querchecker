# Projektwissen: Querchecker — Allgemein

## Ziel
Preisvergleichs-App für Elektronik/Hardware. Willhaben-Inserate durchsuchen, filtern, bewerten, mit Notizen versehen. Cross-Referenzierung mit Geizhals (Marktpreisvergleich) geplant.

---

## Stack

| Schicht | Technologie |
|---|---|
| Frontend | Angular 21+, Angular Material V3, @ngrx/signals |
| Backend | Spring Boot 3.3.4, Java 21, Lombok, SpotBugs |
| Datenbank | PostgreSQL 16 via Docker |
| API-Codegen | openapi-generator-cli (devDependency im Frontend) |
| Prod | Docker, nginx, Traefik (SSL via Let's Encrypt) |

---

## Ports

| Port | Service |
|---|---|
| `14070` | Spring Boot Backend |
| `14071` | PostgreSQL (Docker, `14071:5432`) |
| `14072` | Angular (ng serve) |

---

## Naming-Konventionen

| Abkürzung | Bedeutung |
|---|---|
| `wh` | Willhaben |

- **PK** immer `id` (intern)
- **FK** trägt Entitätsnamen als Prefix: `whListingId` (nicht `listingId`)
- **Externe IDs** mit Plattform-Prefix: `whId` = Willhaben-interne ID, `areaId` = Standort-ID
- **Entitäts-/Klassennamen** mit Abkürzung: `WhListing`, `WhListingRepository`
- **DTOs** mit Querchecker- oder Wh-Prefix: `QuercheckerListingDto`, `WhListingDetailDto`

---

## Monorepo-Struktur

```
querchecker/
├── backend/                ← Spring Boot (Maven)
├── frontend/               ← Angular 21+
├── docker-compose.yml      ← Dev: nur PostgreSQL
├── docker-compose.prod.yml ← Prod: nginx + backend + postgres
└── README.md
```

---

## Implementierte Features

- Willhaben-Suche mit Filtern (Stichwort, Standort, Kategorie, Preisspanne, Paylivery)
- Förderband-UI: SEARCH → LISTINGS → DETAIL State-Machine mit Animationen
- Thumbnails in Listing-Cards
- Notizen je Inserat (WhListingDetail.note)
- Rating UP/DOWN/null je Inserat + Filterung nach Rating (UP, UP_NULL, DOWN, ALL)
- View-Counter (viewCount, lastViewedAt) mit 60s Throttle
- Hierarchische Standort- und Kategoriefilter (multi-level Baumnavigation)
- Sortierung der Ergebnisse

## Geplante Features

- [ ] Marktpreisvergleich via Geizhals
- [ ] Mehrere Suchprofile / gespeicherte Suchen
- [ ] Automatische Benachrichtigung bei neuen Inseraten
- [ ] Mobile-optimiertes Layout
- [ ] Mehr Plattformen (eBay Kleinanzeigen, Shpock…)

---

## Dev-Workflow

```bash
docker compose up -d              # PostgreSQL starten
cd backend && mvn spring-boot:run # Backend (erstmaliger Start)
cd frontend && npm start          # Frontend
cd frontend && npm run generate-api  # nach Backend-API-Änderungen
```

**Source changes**: `mvn compile` → spring-boot-devtools startet Spring-Context neu.
JVM muss nicht neu gestartet werden.

---

## Deployment

```bash
docker compose -f docker-compose.prod.yml up -d
```

Traefik-Labels in `docker-compose.prod.yml` anpassen (Domain, certresolver).
