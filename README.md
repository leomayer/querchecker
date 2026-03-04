# Querchecker

Preisvergleichs-App für Elektronik/Hardware. Willhaben-Suchen durchführen, Ergebnisse mit anderen Plattformen (Geizhals etc.) cross-referenzieren.

## Stack

| Schicht     | Technologie                                       |
| ----------- | ------------------------------------------------- |
| Frontend    | Angular 21+, Angular Material V3                  |
| Backend     | Spring Boot, Java 21, Lombok, SpotBugs            |
| Datenbank   | PostgreSQL 16 (Docker)                            |
| API-Codegen | openapi-generator-cli (devDependency im Frontend) |

## Ports

| Port  | Service              |
|-------|----------------------|
| 14070 | Spring Boot Backend  |
| 14071 | PostgreSQL (Docker)  |
| 14072 | Angular (ng serve)   |

## Quickstart (Dev)

```bash
# 1. PostgreSQL starten
docker compose up -d

# 2. Backend starten (eigenes Terminal)
cd backend && mvn spring-boot:run

# 3. Frontend starten (eigenes Terminal)
cd frontend && npm start

# 4. API-Client generieren (nach Backend-Änderungen)
cd frontend && npm run generate-api
```

> Alternativ: Zed Tasks (`ctrl+3` → "task: spawn")

## Struktur

```
querchecker/
├── backend/                ← Spring Boot (Maven)
│   └── src/main/java/at/querchecker/
├── frontend/               ← Angular 21+
│   └── src/app/
│       ├── api/            ← generiert (openapi-generator-cli)
│       ├── core/           ← api-urls.ts (hand-written, stabil)
│       ├── features/       ← Feature-Komponenten
│       └── shared/         ← Wiederverwendbare Komponenten
├── docker-compose.yml      ← Dev: nur PostgreSQL
├── docker-compose.prod.yml ← Prod: nginx + backend
└── README.md
```

## Features

- Suchmaske mit konfigurierbaren Filtern (Location, Kategorie, Gratis, Freitext)
- Reaktives Filtering via Angular Signals + `httpResource`
- Ergebnisliste mit Per-Zeile Kreuzsuche auf anderen Plattformen
- Notizen je Inserat (PostgreSQL via `ListingNote`)
- Marktpreisvergleich (günstig/teuer-Einschätzung)

## Deployment

```bash
docker compose -f docker-compose.prod.yml up -d
```

Traefik-Labels in `docker-compose.prod.yml` anpassen (Domain, certresolver).
