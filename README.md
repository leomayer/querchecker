# Querchecker

Preisvergleichs-App für Elektronik/Hardware. Willhaben-Suchen durchführen, Ergebnisse mit anderen Plattformen (Geizhals etc.) cross-referenzieren.

## Ports

| Port  | Service                      |
|-------|------------------------------|
| 14070 | Spring Boot Backend          |
| 14071 | PostgreSQL (Docker)          |
| 14072 | Angular (ng serve)           |

## Quickstart (Dev)

```bash
# 1. PostgreSQL starten
docker compose up -d

# 2. Backend starten (eigenes TERMINAL
cd backend
mvn spring-boot:run

# 3. Frontend starten (eigenes Terminal)
cd frontend
npm install
ng serve --port 14072

# 4. API-Client generieren (nach Backend-Änderungen)
cd frontend
npm run generate-api
```

## Struktur

```
querchecker/
├── backend/                ← Spring Boot (Maven)
├── frontend/               ← Angular 20+
├── docker-compose.yml      ← Dev: nur PostgreSQL
├── docker-compose.prod.yml ← Prod: nginx + backend
└── README.md
```

## Deployment

```bash
docker compose -f docker-compose.prod.yml up -d
```

Traefik-Labels in `docker-compose.prod.yml` anpassen (Domain, certresolver).
