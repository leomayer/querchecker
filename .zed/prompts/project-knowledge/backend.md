# Projektwissen: Querchecker

---

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
### Plattform-Abstraktion (Willhaben-first)
- Willhaben ist der erste und aktuell einzige Provider
- Cross-Search / Detail-Ansicht (z.B. Geizhals): Platzierung noch offen — Überlegung ist, dass  Details in Zone 3 angezeigt werden, die Übersicht in Zone 2
- Provider-spezifische Logik bleibt isoliert, damit Willhaben später ersetzt oder ergänzt werden kann ohne strukturelle UI-Änderungen
- Spiegelt die Backend-Strategie (`BaseListing` / `@Inheritance`) auf der Frontend-Seite wider

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
