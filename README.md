# Querchecker

Preisvergleichs-App für Elektronik/Hardware. Willhaben-Inserate durchsuchen, filtern, bewerten und mit Notizen versehen. Cross-Referenzierung mit anderen Plattformen (Geizhals etc.) geplant.

## Vision

Querchecker hilft beim Schnäppchenkauf: Man startet eine Suche auf Willhaben, sieht die Ergebnisse direkt in einer übersichtlichen Oberfläche, bewertet Inserate mit Daumen hoch/runter, schreibt Notizen, und kann so den Überblick behalten — insbesondere bei langen Suchen mit vielen Ergebnissen. Zukünftig soll der Preis jedes Inserats mit dem Marktpreis (Geizhals) verglichen werden, um einschätzen zu können, ob ein Angebot wirklich günstig ist.

## Status

Aktiv in Entwicklung (v0.0.1-SNAPSHOT). Grundfunktionen implementiert:

- Willhaben-Suche mit Filtern (Stichwort, Standort, Kategorie, Preisspanne, Paylivery)
- Förderband-UI: Dreistufige Navigation (Suchmaske → Ergebnisliste → Detailansicht) mit Animationen
- Thumbnails in Listing-Cards + vollständige Bildergalerie in Detailansicht
- Notizen je Inserat (Autosave)
- Rating (Daumen hoch/runter/neutral) + Filterung nach Rating
- Interesse-Level (LOW/MEDIUM/HIGH) + Tags je Inserat
- View-Counter mit Zeitstempel (60s Throttle)
- Hierarchische Standort- und Kategoriefilter (Bundesland → Bezirk, Baumnavigation)
- Letzte Suche wird im Browser gespeichert (localStorage, 3 Tage gültig)
- Settings-Seite (Theme-Toggle, Datenbereinigung)

## Geplante Features

- [ ] Marktpreisvergleich via Geizhals-API
- [ ] Mehrere Suchprofile / gespeicherte Suchen
- [ ] Mobile-optimiertes Layout
- [ ] Mehr Plattformen (eBay Kleinanzeigen, Shpock…)

## Stack

| Schicht   | Technologie                                    |
| --------- | ---------------------------------------------- |
| Frontend  | Angular 21+, Angular Material V3, @ngrx/signals |
| Backend   | Spring Boot 3.3.4, Java 21                     |
| Datenbank | PostgreSQL 16 (Docker)                         |
| Prod      | Docker, nginx, Traefik (SSL via Let's Encrypt) |

## Ports

| Port  | Service              |
|-------|----------------------|
| 14070 | Spring Boot Backend  |
| 14071 | PostgreSQL (Docker)  |
| 14072 | Angular (ng serve)   |

## Quickstart (Dev)

```bash
# PostgreSQL starten
docker compose up -d

# Backend starten (eigenes Terminal)
cd backend && mvn spring-boot:run

# Frontend starten (eigenes Terminal)
cd frontend && npm start
```

## Struktur

```
querchecker/
├── backend/                ← Spring Boot (Maven)
├── frontend/               ← Angular 21+
├── docker-compose.yml      ← Dev: nur PostgreSQL
└── docker-compose.prod.yml ← Prod: vollständiger Stack
```

## Deployment

```bash
docker compose -f docker-compose.prod.yml up -d
```

Traefik-Labels in `docker-compose.prod.yml` anpassen (Domain, certresolver).

### GPU-Beschleunigung (LLM-Extraktionsmodelle)

Die KI-Extraktionsmodelle laufen über llama.cpp. Im Prod-Profil ist `gpu-layers: 999` gesetzt — llama.cpp lädt automatisch so viele Schichten auf die GPU wie das Modell hat.

**Kein GPU vorhanden → CPU erzwingen** (in `docker-compose.prod.yml`):

```yaml
environment:
  QUERCHECKER_DL_GPU_LAYERS: 0
```

Voraussetzungen für GPU-Betrieb:
- NVIDIA GPU mit CUDA-Unterstützung
- CUDA Toolkit auf dem Host installiert
- Die in der JAR enthaltene java-llama.cpp-Native-Bibliothek unterstützt CUDA auf Linux x86_64 out of the box

Falls kein kompatibles GPU erkannt wird, fällt llama.cpp beim Start automatisch auf CPU zurück (Hinweis im Log).

### Modell-Dateien

Die GGUF-Modelldateien sind **nicht** im Docker-Image enthalten. Download-Skripte einmalig ausführen:

```bash
# Im laufenden Backend-Container:
python3 src/main/resources/models/download_nuextract.py
python3 src/main/resources/models/download_nuextract15.py
python3 src/main/resources/models/download_qwen25.py
```
