# DL-Feature: Produktname-Extraktion für Querchecker

**Version**: 0.1.0

## Übersicht & Ziel

Automatische Extraktion von Produktnamen/Modellbezeichnungen aus Willhaben-Inseraten für
Kreuzsuchen (Geizhals, Brave etc.) – lokal, kein Cloud-API-Call, kein externer Service.

**Ansatz**: Extractive QA mit deutschen BERT-Modellen via DJL (Deep Java Library) in Spring Boot.
Das Modell markiert eine Textspanne direkt aus Titel + Beschreibung – es erfindet nichts.

**Einstiegsmodelle** (parallel testen):

| Modell                                              | Größe  | Bemerkung                                                                          |
| --------------------------------------------------- | ------ | ---------------------------------------------------------------------------------- |
| `deepset/gelectra-base-germanquad`                  | ~440MB | Hauptkandidat, deutsch-nativ, auf GermanQuAD trainiert                             |
| `deutsche-telekom/bert-multi-english-german-squad2` | ~700MB | Vergleichsmodell, multilingual BERT, auf SQuAD2 trainiert, ONNX via onnx-community |

**LLM-Migrationspfad**: Entity-Design und Interface bleiben unverändert – neue
`LlamaExtractionModel`-Implementierung des gleichen Interface, JSON-Parsing nötig.

---

## Referenz: Entity-Design

### `ItemText` – plattformunabhängiger Text-Speicher

```
ItemText
├── id
├── whListing       (ManyToOne, nullable)  ← null nach WH-Löschung, mehrere Records möglich
├── source          Enum (WH, GEIZHALS, ...)
├── title           String                 ← HTML-stripped (via Jsoup)
├── description     String                 ← HTML-stripped, Volltext, kein Token-Limit
├── contentHash     String                 ← SHA256(title + description), Change-Detection
└── fetchedAt       LocalDateTime
```

Bei WH-Inhaltsänderung wird ein **neuer Record** angelegt – kein Update des bestehenden.
Alte Records bleiben erhalten (mit ihren `DlExtractionRun`s) bis TTL-Cleanup greift.
`whListing` → `ItemText` ist damit **1:n** – aktuellster Record via `ORDER BY fetchedAt DESC`.
HTML wird beim Speichern via Jsoup gestripped – `ItemText` enthält immer Plain Text.

### `DlModelConfig` – Modell-Konfiguration (DB-Tabelle)

```
DlModelConfig
├── id
├── modelName     String   ← eindeutig, Logik-Schlüssel – muss mit ExtractionModel.getName() übereinstimmen
├── modelVersion  String   ← aus DB, nicht aus Java-Klasse
├── temperature   Float    ← gehört zum Modell, nicht zum einzelnen Run
├── maxTokens     Integer  ← BERT=512, LLaMA=4096+ – aus DB, nicht hardcodiert in Java
├── source        Enum (HUGGINGFACE, LOCAL)
├── localPath     String (nullable)
└── active        Boolean  ← false = Modell deaktiviert, Daten bleiben erhalten
```

Modell-Konfiguration in DB statt `application.yml` – zur Laufzeit änderbar.
`active=false` statt Löschen – historische Runs bleiben nachvollziehbar.
`modelVersion` und `maxTokens` kommen aus der DB – `ExtractionModel`-Interface
kennt nur `getName()` als einzige Verbindung zur DB-Konfiguration.

### `DlExtractionRun` – ein Lauf pro Modell pro Item

```
DlExtractionRun
├── id
├── itemText        (ManyToOne → ItemText)
├── modelConfig     (ManyToOne → DlModelConfig)  ← FK-Konvention: Entitätsname ohne "Id"
├── prompt          String                 ← aufgelöster Prompt, für Reproduzierbarkeit
├── inputHash       String                 ← SHA256(title + description)
├── extractedAt     LocalDateTime
├── status          Enum (INIT, PENDING, DONE, FAILED, NO_IMPLEMENTATION, RE_EVALUATE)
├── errorMessage    String (nullable, max 500 Zeichen, nur bei FAILED)
└── createdAt       LocalDateTime  ← für Scheduler: hängende INIT-Runs erkennen
```

**Status State Machine:**

```
INIT ──────────────→ PENDING → DONE ──→ RE_EVALUATE ─┐
                              ↘ FAILED ──→ RE_EVALUATE ─┤
                                                        └→ PENDING → DONE
                                                                   ↘ FAILED
INIT → NO_IMPLEMENTATION  (terminal, kein @Component gefunden)
```

| Status              | Bedeutung                                                                         |
| ------------------- | --------------------------------------------------------------------------------- |
| `INIT`              | Neu angelegt, wartet auf Start                                                    |
| `PENDING`           | Modell läuft aktiv (Modell setzt selbst)                                          |
| `DONE`              | Erfolgreich abgeschlossen                                                         |
| `FAILED`            | Fehler – `errorMessage` befüllt, max 500 Zeichen, Stacktrace ins Log              |
| `NO_IMPLEMENTATION` | Kein `@Component` für `modelName` gefunden – terminal, blockiert allDone nicht    |
| `RE_EVALUATE`       | Neu auswerten – bestehende `DlExtractionTerm`-Einträge werden vor Re-Run gelöscht |

Bei WH-Inhaltsänderung: **neuer `ItemText`-Record** wird angelegt →
neue `DlExtractionRun`s mit `INIT`. Alter Record + alte Runs bleiben auf `DONE` erhalten.
`RE_EVALUATE` wird vom Scheduler behandelt, aber mit vorherigem Term-Cleanup.

**Optional: INIT als Retry-Mechanismus**
Ein `@Scheduled`-Job kann INIT-Runs die älter als X Minuten sind automatisch neu starten –
nützlich bei Server-Problemen oder abgebrochenen Prozessen:

```java
// Alle paar Minuten: hängende INIT-Runs neu starten
runRepo.findByStatusAndCreatedAtBefore(INIT, LocalDateTime.now().minusMinutes(5))
    .forEach(run -> CompletableFuture.runAsync(
        () -> extractionService.runModel(run)));
```

### `DlExtractionTerm` – n Terme pro Run

```
DlExtractionTerm
├── id
├── run                  (ManyToOne → DlExtractionRun)
├── term                 String              ← Modell-Output
├── confidence           Float               ← 0.0–1.0
├── userCorrectedTerm    String (nullable)   ← spätere Trainingsdaten
├── userCorrectedAt      LocalDateTime (nullable)
└── correctionNote       String (nullable)
```

Sortierung: `ORDER BY confidence DESC` – Filter greift VOR DB-Insert, kein `LIMIT` nötig.

### `DlCategoryPrompt` – Prompt je Kategorie

```
DlCategoryPrompt
├── id
├── whCategory      (ManyToOne → WhCategory, nullable)  ← null = Default-Prompt
├── prompt          String
└── updatedAt       LocalDateTime
```

---

## Referenz: Service-Verantwortlichkeiten

| Service                    | Verantwortung                                                                                                                                                                                                              |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ItemTextService`          | `findOrCreateOrUpdate(WhListing)` – aktuellsten ItemText zurückgeben oder neuen Record anlegen bei Inhaltsänderung                                                                                                         |
| `ItemTextCleanupScheduler` | TTL-Cleanup: alte ItemText-Records löschen (konfigurierbar, schützt userCorrectedTerm)                                                                                                                                     |
| `DlOrchestrationService`   | Vorbereitung: aktive `DlModelConfig` aus DB, Duplikat-Check, Component-Check, Prompt auflösen, Run anlegen (INIT oder NO_IMPLEMENTATION), CompletableFutures starten, `allOf().thenRun()` als primärer allDone-Mechanismus |
| `DlExtractionService`      | Ausführung: Context aufbauen, Token-Kürzung via `modelConfig.getMaxTokens()`, `ExtractionModel.extract()`, weiterleiten an `DlPersistenceService` – Status PENDING→DONE/FAILED                                             |
| `DlFilterService`          | Filter (unabhängig vom Modell): confidence↓ → top-k → min-confidence – VOR DB-Insert, kein LIMIT in Query                                                                                                                  |
| `DlPersistenceService`     | DB-Insert gefilterte Terms, Run-Status setzen, `checkAllDone()` als DB-Fallback für Restart-Szenarien, Event publizieren                                                                                                   |
| `DlPromptResolver`         | Prompt auflösen: kategorie-spezifisch → Default (DB) → hardcodierter Fallback                                                                                                                                              |
| `DlCategoryPromptSeeder`   | Idempotente Befüllung von `DlCategoryPrompt` nach Kategorie-Refresh                                                                                                                                                        |

## Referenz: Gesamtfluss

```
WhSearchService.upsertListing()
    → ItemText anlegen (falls nicht vorhanden)
    → DlOrchestrationService.scheduleExtraction(itemText)
        │
        ├── 1. Aktive DlModelConfig aus DB: findByActiveTrue()
        ├── 2. Duplikat-Check: existsByItemTextAndModelConfigAndStatus(DONE)?
        │       → bereits DONE? → überspringen
        ├── 3. DlPromptResolver.resolve(itemText) → String prompt
        │       → WhCategory → DlCategoryPrompt (kategorie-spezifisch oder Default)
        ├── 4. Component-Check: ExtractionModel für modelName vorhanden?
        │       → NEIN: Run anlegen mit status=NO_IMPLEMENTATION (terminal) → fertig
        │       → JA:   Run anlegen mit status=INIT
        ├── 5. CompletableFutures starten (non-blocking)
        └── 6. CompletableFuture.allOf(...).thenRun()    ← primärer allDone-Mechanismus
                → alle Futures fertig?
                    → ApplicationEventPublisher.publishEvent()
                        → SseHub.broadcast("dl-extract", DlExtractionDonePayload) → Angular
                DB-Fallback: DlPersistenceService.checkAllDone() nach jedem Run
                → fängt Restart-Szenarien ab (Futures verloren, DB-Status korrekt)

DlExtractionService.runModel(run)           ← pro Modell, parallel
    ├── status=PENDING  (Modell setzt selbst)
    ├── Context: title + "\n\n" + description
    │   kürzen via run.getModelConfig().getMaxTokens()
    ├── ExtractionModel.extract(itemText, run.getPrompt(), maxTokens)
    └── DlPersistenceService.saveResults()
            → DlFilterService.filter()      (confidence↓ → top-k → min-confidence)
            → DlExtractionTermRepository.saveAll()
            → status=DONE oder FAILED + errorMessage
            → checkAllDone() als DB-Fallback
                → alle Runs DONE/FAILED/NO_IMPLEMENTATION?
                    → ApplicationEventPublisher (nur wenn allOf() nicht gefeuert hat)
```

## Referenz: Konfiguration (`application.yml`)

Modell-Konfiguration (`modelName`, `temperature`, `source`, `localPath`, `active`)
wird in `DlModelConfig`-Tabelle verwaltet – zur Laufzeit änderbar ohne Neustart.

Globale Filter-Konfiguration bleibt in `application.yml`:

```yaml
# Evaluierungsphase
querchecker:
  dl:
    min-confidence: 0.0
    top-k: 5        # Filter VOR DB-Insert – kein LIMIT in Query nötig
                    # store-all-terms entfernt: redundant, top-k=groß + min-confidence=0.0
                    # ergibt dasselbe Ergebnis

# Produktivbetrieb (nach Evaluierung)
querchecker:
  dl:
    min-confidence: 0.65
    top-k: 3
```

**Docker**: Entwicklung: `~/.djl.ai:/root/.djl.ai` als Volume. Produktion: `COPY models/ /models/`.

### Modell-Download (ONNX, lokal)

Beide Modelle werden als vorexportierte ONNX-Dateien von HuggingFace heruntergeladen.
Pro Modell werden `tokenizer.json` und `model.onnx` (quantisiert) benötigt.

```bash
# Verzeichnisstruktur: backend/src/main/resources/models/<model-name>/
#   ├── tokenizer.json
#   └── model.onnx

# 1. gelectra-base-germanquad (~111 MB quantisiert)
#    Quelle: onnx-community/gelectra-base-germanquad-ONNX
cd backend/src/main/resources/models/gelectra-base-germanquad
curl -L "https://huggingface.co/onnx-community/gelectra-base-germanquad-ONNX/resolve/main/tokenizer.json" -o tokenizer.json
curl -L "https://huggingface.co/onnx-community/gelectra-base-germanquad-ONNX/resolve/main/onnx/model_quantized.onnx" -o model.onnx

# 2. bert-multi-english-german-squad2 (~111 MB quantisiert)
#    Quelle: onnx-community/bert-multi-english-german-squad2-ONNX
cd backend/src/main/resources/models/bert-multi-english-german-squad2
curl -L "https://huggingface.co/onnx-community/bert-multi-english-german-squad2-ONNX/resolve/main/tokenizer.json" -o tokenizer.json
curl -L "https://huggingface.co/onnx-community/bert-multi-english-german-squad2-ONNX/resolve/main/onnx/model_quantized.onnx" -o model.onnx
```

**Hinweis**: `model_quantized.onnx` wird als `model.onnx` gespeichert – DJL/ORT erwartet diesen Dateinamen.
Für bessere Qualität kann stattdessen `onnx/model.onnx` (full precision, ~440 MB) verwendet werden.

---

## Prompt A – Maven & Flyway Setup

*Parallelisierbar mit: nichts, muss zuerst*

### Aufgabe

1. Flyway-Dependency in `pom.xml` hinzufügen
2. Flyway in `application.yml` konfigurieren
3. DJL-Dependencies hinzufügen
4. DL-Package-Struktur anlegen: `at.querchecker.dl.{entity,repository,service,controller}`

### Maven Dependencies

```xml
<!-- Flyway -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <!-- Version von Spring Boot BOM verwaltet -->
</dependency>

<!-- DJL -->
<dependency>
    <groupId>ai.djl.huggingface</groupId>
    <artifactId>tokenizers</artifactId>
    <version>0.31.0</version>
</dependency>
<dependency>
    <groupId>ai.djl.onnxruntime</groupId>
    <artifactId>onnxruntime-engine</artifactId>
    <version>0.31.0</version>
</dependency>
```

### Flyway-Konfiguration

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## Prompt B – DlCategoryPrompt: Tabelle, Entity, Seeder

*Parallelisierbar mit: Prompt C (andere Tabellen)*

### Aufgabe

1. Flyway Migration-Script anlegen (DDL only – keine Daten)
2. Entity `DlCategoryPrompt` anlegen
3. Repository `DlCategoryPromptRepository` anlegen
4. `DlCategoryPromptDefinitions` anlegen (zentrale Prompt-Liste)
5. `DlCategoryPromptSeeder` anlegen
6. `WhRefreshScheduler` anpassen: `seedIfAbsent()` nach Kategorie-Refresh aufrufen

### Flyway Migration

**Datei**: `src/main/resources/db/migration/V{n}__create_dl_category_prompt.sql`

```sql
CREATE TABLE dl_category_prompt (
    id             BIGSERIAL PRIMARY KEY,
    wh_category_id BIGINT REFERENCES wh_category(id) ON DELETE SET NULL,
    prompt         TEXT      NOT NULL,
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dl_category_prompt_category UNIQUE (wh_category_id)
);

CREATE INDEX idx_dl_category_prompt_category ON dl_category_prompt(wh_category_id);
```

### Entity

```java
@Entity
@Table(name = "dl_category_prompt")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DlCategoryPrompt {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wh_category_id")
    private WhCategory whCategory;  // null = Default-Prompt

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    public void updateTimestamp() { this.updatedAt = LocalDateTime.now(); }
}
```

### Repository

```java
public interface DlCategoryPromptRepository extends JpaRepository<DlCategoryPrompt, Long> {
    Optional<DlCategoryPrompt> findByWhCategory(WhCategory category);

    @Query("SELECT p FROM DlCategoryPrompt p WHERE p.whCategory IS NULL")
    Optional<DlCategoryPrompt> findDefault();
}
```

### Prompt-Definitionen

```java
/**
 * Zentrale Definition aller Kategorie-Prompts.
 * Neue Kategorien hier ergänzen – DlCategoryPromptSeeder liest diese Liste.
 * Kategorienamen müssen exakt mit WhCategory.name übereinstimmen.
 */
public final class DlCategoryPromptDefinitions {

    private DlCategoryPromptDefinitions() {}

    /** Fallback wenn keine Kategorie passt */
    public static final String DEFAULT =
        "Was ist der genaue Produktname oder die Modellbezeichnung?";

    public static final Map<String, String> CATEGORY_PROMPTS = Map.ofEntries(
        // Computer & Zubehör
        Map.entry("Laptop / Notebook",
            "Welches Laptop- oder Notebook-Modell wird verkauft?"),
        Map.entry("Smartphone & Handy",
            "Welches Smartphone- oder Handy-Modell wird verkauft?"),
        Map.entry("Tablet",
            "Welches Tablet-Modell wird angeboten?"),
        Map.entry("PC-Komponenten",
            "Welches PC-Bauteil wird angeboten, z.B. Grafikkarte, CPU oder RAM?"),
        Map.entry("Monitor & Beamer",
            "Welcher Monitor oder Beamer wird angeboten?"),
        Map.entry("Drucker & Scanner",
            "Welcher Drucker oder Scanner wird angeboten?"),
        Map.entry("Netzwerk & Server",
            "Welches Netzwerkgerät oder welcher Server wird angeboten?"),
        Map.entry("Foto & Kamera",
            "Welche Kamera oder welches Fotogerät wird angeboten?"),
        Map.entry("TV & Audio",
            "Welches TV- oder Audiogerät wird angeboten?"),
        Map.entry("Konsolen & Games",
            "Welche Spielkonsole oder welches Spiel wird angeboten?"),
        // Haushaltsgeräte
        Map.entry("Haushaltsgeräte",
            "Welches Haushaltsgerät wird angeboten?"),
        Map.entry("Staubsauger",
            "Welcher Staubsauger wird angeboten?"),
        Map.entry("Waschmaschine & Trockner",
            "Welche Waschmaschine oder welcher Trockner wird angeboten?")
    );
}
```

### Seeder

```java
/**
 * Befüllt DlCategoryPrompt idempotent nach Kategorie-Refresh.
 * Mehrfachaufruf sicher – bei bereits befüllter Tabelle kein Effekt.
 */
@Component
@RequiredArgsConstructor
public class DlCategoryPromptSeeder {

    private final DlCategoryPromptRepository promptRepo;
    private final WhCategoryRepository categoryRepo;

    public void seedIfAbsent() {
        if (categoryRepo.count() == 0) return;  // Kategorien noch nicht geladen
        if (promptRepo.count() > 0) return;      // bereits befüllt

        // Default-Prompt
        promptRepo.save(DlCategoryPrompt.builder()
            .whCategory(null)
            .prompt(DlCategoryPromptDefinitions.DEFAULT)
            .build());

        // Kategorie-spezifische Prompts
        DlCategoryPromptDefinitions.CATEGORY_PROMPTS.forEach((name, prompt) ->
            categoryRepo.findByNameAndLevel(name, 0)
                .ifPresent(cat -> promptRepo.save(DlCategoryPrompt.builder()
                    .whCategory(cat)
                    .prompt(prompt)
                    .build()))
        );
    }
}
```

### WhRefreshScheduler anpassen

```java
// Nach erfolgreichem Kategorie-Refresh:
dlCategoryPromptSeeder.seedIfAbsent();
```

### Unit Tests: DlCategoryPromptSeeder

```java
@ExtendWith(MockitoExtension.class)
class DlCategoryPromptSeederTest {

    @Mock WhCategoryRepository categoryRepo;
    @Mock DlCategoryPromptRepository promptRepo;
    @InjectMocks DlCategoryPromptSeeder seeder;

    @Test
    void seedIfAbsent_doesNothing_whenCategoriesEmpty() {
        when(categoryRepo.count()).thenReturn(0L);
        seeder.seedIfAbsent();
        verify(promptRepo, never()).save(any());
    }

    @Test
    void seedIfAbsent_doesNothing_whenAlreadySeeded() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.count()).thenReturn(3L);
        seeder.seedIfAbsent();
        verify(promptRepo, never()).save(any());
    }

    @Test
    void seedIfAbsent_savesDefaultPrompt_whenCategoriesPresent() {
        when(categoryRepo.count()).thenReturn(5L);
        when(promptRepo.count()).thenReturn(0L);
        seeder.seedIfAbsent();
        verify(promptRepo, atLeastOnce()).save(argThat(p -> p.getWhCategory() == null));
    }
}
```

---

## Prompt C – Restliche Entities & Flyway Migrations

*Parallelisierbar mit: Prompt B*

### Aufgabe

1. Flyway Migration-Scripts für `dl_model_config`, `item_text`, `dl_extraction_run`, `dl_extraction_term`
2. Entities anlegen: `DlModelConfig`, `ItemText`, `DlExtractionRun`, `DlExtractionTerm`
3. Repositories anlegen
4. `DlModelConfig` mit initialen Einträgen befüllen (analog zu `DlCategoryPromptSeeder`)

### Flyway Migrations

**`V{n+1}__create_dl_model_config.sql`**

```sql
CREATE TABLE dl_model_config (
    id            BIGSERIAL PRIMARY KEY,
    model_name    VARCHAR(255) NOT NULL UNIQUE,
    model_version VARCHAR(100),
    temperature   FLOAT        NOT NULL DEFAULT 0.0,
    max_tokens    INTEGER      NOT NULL DEFAULT 512,
    source        VARCHAR(20)  NOT NULL DEFAULT 'HUGGINGFACE',
    local_path    VARCHAR(500),
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Initiale Einträge (Evaluierungsphase)
INSERT INTO dl_model_config (model_name, model_version, temperature, max_tokens, source, active)
VALUES
    ('gelectra-base-germanquad', '2024-03', 0.0, 512, 'HUGGINGFACE', true),
    ('xlm-roberta-base-squad2',  '2024-03', 0.0, 512, 'HUGGINGFACE', true);
```

**`V{n+2}__create_item_text.sql`**

```sql
CREATE TABLE item_text (
    id             BIGSERIAL PRIMARY KEY,
    wh_listing_id  BIGINT REFERENCES wh_listing(id) ON DELETE SET NULL,
    -- 1:n Beziehung: bei Inhaltsänderung neuer Record, alter bleibt erhalten
    source         VARCHAR(50) NOT NULL,
    title          TEXT        NOT NULL,  -- HTML-stripped via Jsoup
    description    TEXT        NOT NULL,  -- HTML-stripped via Jsoup
    content_hash   VARCHAR(64) NOT NULL,  -- SHA256(title + description), Change-Detection
    fetched_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_item_text_wh_listing   ON item_text(wh_listing_id);
CREATE INDEX idx_item_text_content_hash ON item_text(content_hash);
CREATE INDEX idx_item_text_fetched_at   ON item_text(fetched_at);  -- für TTL-Cleanup
```

**`V{n+3}__create_dl_extraction_run.sql`**

```sql
CREATE TABLE dl_extraction_run (
    id               BIGSERIAL PRIMARY KEY,
    item_text_id     BIGINT NOT NULL REFERENCES item_text(id) ON DELETE CASCADE,
    model_config_id  BIGINT NOT NULL REFERENCES dl_model_config(id),
    prompt           TEXT   NOT NULL,
    input_hash       VARCHAR(64) NOT NULL,
    extracted_at     TIMESTAMP,
    -- Status: INIT, PENDING, DONE, FAILED, NO_IMPLEMENTATION, RE_EVALUATE
    status           VARCHAR(20)  NOT NULL DEFAULT 'INIT',
    error_message    VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dl_extraction_run_item_text    ON dl_extraction_run(item_text_id);
CREATE INDEX idx_dl_extraction_run_model_config ON dl_extraction_run(model_config_id);
CREATE INDEX idx_dl_extraction_run_status       ON dl_extraction_run(status);
```

**`V{n+4}__create_dl_extraction_term.sql`**

```sql
CREATE TABLE dl_extraction_term (
    id                   BIGSERIAL PRIMARY KEY,
    run_id               BIGINT NOT NULL REFERENCES dl_extraction_run(id) ON DELETE CASCADE,
    term                 TEXT   NOT NULL,
    term_type            VARCHAR(100),
    confidence           FLOAT,
    user_corrected_term  TEXT,
    user_corrected_at    TIMESTAMP,
    correction_note      TEXT
);

CREATE INDEX idx_dl_extraction_term_run ON dl_extraction_term(run_id);
```

### Entities & Repositories

Analog zu `DlCategoryPrompt` – Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`.
`DlModelConfig.source` als `@Enumerated(EnumType.STRING)`.
`DlExtractionRun.status` als `@Enumerated(EnumType.STRING)` –
Werte: `INIT, PENDING, DONE, FAILED, NO_IMPLEMENTATION, RE_EVALUATE`.
`DlExtractionRun.errorMessage` mit `@Column(length = 500)`.
`DlExtractionRun.modelConfig` als `@ManyToOne` – FK-Konvention: Entitätsname ohne "Id".

**Enum-Strategie**: `@Enumerated(EnumType.STRING)` speichert den Enum-Namen als `VARCHAR`
in PostgreSQL – kein nativer PG-Enum-Typ. Gängige Praxis in Spring Boot / Hibernate:

- PG-Enums sind nicht erweiterbar ohne DROP+CREATE – problematisch mit Flyway
- `VARCHAR(20)` + `EnumType.STRING` ist flexibel, Flyway-freundlich, kein Konfigurationsaufwand
- Gilt für alle Enums im Projekt: `ExtractionStatus`, `ItemSource`, `ModelSource`

`DlModelConfigRepository` benötigt:

```java
List<DlModelConfig> findByActiveTrue();
```

---

## Prompt D – ItemText-Erstellung in WhSearchService

*Setzt Prompt C voraus*

> **Hinweis**: Prompt D ist bereits teilweise implementiert (`findOrCreate()`).
> Diese Version ersetzt die bestehende Implementierung vollständig.
> Änderungen am bestehenden Code nötig:
> 
> - `findOrCreate()` → `findOrCreateOrUpdate()` mit neuer Record-Strategie
> - `contentHash` hinzufügen
> - Jsoup statt `HtmlUtils`
> - STALE-Logik entfernen (war in früherer Version, jetzt nicht mehr nötig)
> - TTL-Cleanup-Job hinzufügen

### Aufgabe

1. Jsoup-Dependency prüfen (transitiv via Spring Boot vorhanden)
2. `ItemTextService.findOrCreate()` → `findOrCreateOrUpdate()` ersetzen
3. `WhSearchService.upsertListing()` Methodenaufruf anpassen
4. `ItemTextCleanupScheduler` anlegen
5. `application.yml` mit TTL-Konfiguration ergänzen

### WhSearchService anpassen

```java
// WhSearchService.upsertListing() – bestehende Logik ergänzen
WhListing listing = // ... bestehende Upsert-Logik
itemTextService.findOrCreateOrUpdate(listing);  // findOrCreate() ersetzen
return listing;
```

### `ItemTextService.findOrCreateOrUpdate()`

Strategie: bei Inhaltsänderung **neuer Record** – kein Update.
Alter Record + alte `DlExtractionRun`s bleiben auf `DONE` erhalten bis TTL-Cleanup greift.

```java
@Service
@RequiredArgsConstructor
public class ItemTextService {

    private final ItemTextRepository repo;

    public ItemText findOrCreateOrUpdate(WhListing listing) {
        // HTML strippen via Jsoup (robuster als HtmlUtils – behandelt Entities korrekt)
        String cleanTitle       = stripHtml(listing.getTitle());
        String cleanDescription = stripHtml(listing.getDescription());
        String newHash          = sha256(cleanTitle + cleanDescription);

        // Aktuellsten Record holen (1:n – mehrere Records pro WhListing möglich)
        return repo.findFirstByWhListingOrderByFetchedAtDesc(listing)
            .filter(existing -> newHash.equals(existing.getContentHash()))
            .orElseGet(() -> repo.save(ItemText.builder()
                .whListing(listing)
                .source(ItemSource.WH)
                .title(cleanTitle)
                .description(cleanDescription)
                .contentHash(newHash)
                .fetchedAt(LocalDateTime.now())
                .build()));
        // Neuer Record → DlOrchestrationService.scheduleExtraction() in upsertListing()
        // wird mit neuem ItemText aufgerufen → neue INIT-Runs automatisch
    }

    private String stripHtml(String text) {
        if (text == null) return "";
        return Jsoup.parse(text).text();  // Jsoup: transitiv via Spring Boot
    }

    private String sha256(String input) {
        return DigestUtils.sha256Hex(input);  // DigestUtils aus spring-core
    }
}
```

### TTL-Cleanup: `ItemTextCleanupScheduler`

```java
/**
 * Löscht alte ItemText-Records die:
 * - nicht der neueste Record für ihr WhListing sind
 * - älter als konfigurierte retention-days sind
 * - keine userCorrectedTerm in ihren DlExtractionTerms haben (Trainingsdaten schützen)
 * DlExtractionRun + DlExtractionTerm kaskadieren via ON DELETE CASCADE automatisch.
 */
@Component
@RequiredArgsConstructor
public class ItemTextCleanupScheduler {

    private final ItemTextRepository repo;
    private final DlConfig dlConfig;

    @Scheduled(cron = "${querchecker.dl.item-text-cleanup.cron}")
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now()
            .minusDays(dlConfig.getItemTextCleanup().getRetentionDays());
        repo.deleteOutdatedOlderThan(cutoff);
    }
}
```

### Repository Query

```java
// ItemTextRepository
Optional<ItemText> findFirstByWhListingOrderByFetchedAtDesc(WhListing listing);

@Modifying
@Query("""
    DELETE FROM ItemText it
    WHERE it.fetchedAt < :cutoff
    AND it.id NOT IN (
        SELECT MIN(it2.id) FROM ItemText it2
        WHERE it2.whListing = it.whListing
        GROUP BY it2.whListing
        HAVING MAX(it2.fetchedAt) = MAX(it2.fetchedAt)
    )
    AND it.id NOT IN (
        SELECT t.itemText.id FROM DlExtractionTerm t
        WHERE t.userCorrectedTerm IS NOT NULL
    )
""")
void deleteOutdatedOlderThan(@Param("cutoff") LocalDateTime cutoff);
```

### `application.yml` Ergänzung

```yaml
querchecker:
  dl:
    item-text-cleanup:
      retention-days: 10    # ältere ItemText-Records löschen (nicht neuester + kein userCorrectedTerm)
      cron: "0 0 3 * * *"   # täglich um 3 Uhr nachts
```

### Unit Tests: ItemTextService

```java
@ExtendWith(MockitoExtension.class)
class ItemTextServiceTest {

    @Mock ItemTextRepository repo;
    @InjectMocks ItemTextService service;

    @Test
    void findOrCreateOrUpdate_createsNew_whenNoExistingRecord() {
        when(repo.findFirstByWhListingOrderByFetchedAtDesc(any()))
            .thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ItemText result = service.findOrCreateOrUpdate(
            listing("HP LaserJet", "Beschreibung"));

        assertThat(result.getTitle()).isEqualTo("HP LaserJet");
        assertThat(result.getContentHash()).isNotNull();
        verify(repo).save(any());
    }

    @Test
    void findOrCreateOrUpdate_returnsExisting_whenContentUnchanged() {
        ItemText existing = existingItemText("HP LaserJet", "Beschreibung");
        when(repo.findFirstByWhListingOrderByFetchedAtDesc(any()))
            .thenReturn(Optional.of(existing));

        ItemText result = service.findOrCreateOrUpdate(
            listing("HP LaserJet", "Beschreibung"));

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void findOrCreateOrUpdate_createsNewRecord_whenContentChanged() {
        // Neuer Record statt Update – alter bleibt erhalten
        ItemText existing = existingItemText("HP LaserJet", "Alt");
        when(repo.findFirstByWhListingOrderByFetchedAtDesc(any()))
            .thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ItemText result = service.findOrCreateOrUpdate(
            listing("HP LaserJet", "Neu"));

        assertThat(result).isNotSameAs(existing);  // neuer Record
        verify(repo).save(argThat(it ->
            it.getDescription().equals("Neu")));
    }

    @Test
    void findOrCreateOrUpdate_stripsHtml_beforeSaving() {
        when(repo.findFirstByWhListingOrderByFetchedAtDesc(any()))
            .thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ItemText result = service.findOrCreateOrUpdate(
            listing("<b>HP LaserJet</b>", "<p>Beschreibung</p>"));

        assertThat(result.getTitle()).isEqualTo("HP LaserJet");
        assertThat(result.getDescription()).isEqualTo("Beschreibung");
    }

    @Test
    void findOrCreateOrUpdate_contentHashDiffersAfterHtmlStrip() {
        // Hash basiert auf gestripptem Text – nicht auf rohem HTML
        when(repo.findFirstByWhListingOrderByFetchedAtDesc(any()))
            .thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ItemText result = service.findOrCreateOrUpdate(
            listing("<b>HP LaserJet</b>", "Beschreibung"));

        assertThat(result.getContentHash())
            .isEqualTo(DigestUtils.sha256Hex("HP LaserJet" + "Beschreibung"));
    }
}
```

---

## Prompt E – DlPromptResolver

*Setzt Prompt B + C voraus*

### Aufgabe

`DlPromptResolver` implementieren – Prompt-Auflösung kategorie-spezifisch → Default → Fallback.

```java
@Service
@RequiredArgsConstructor
public class DlPromptResolver {

    private final DlCategoryPromptRepository promptRepo;

    public String resolve(ItemText itemText) {
        return Optional.ofNullable(itemText.getWhListing())
            .map(WhListing::getWhCategory)
            .flatMap(promptRepo::findByWhCategory)
            .or(promptRepo::findDefault)
            .map(DlCategoryPrompt::getPrompt)
            .orElse(DlCategoryPromptDefinitions.DEFAULT);
    }
}
```

### Unit Tests: DlPromptResolver

```java
@ExtendWith(MockitoExtension.class)
class DlPromptResolverTest {

    @Mock DlCategoryPromptRepository promptRepo;
    @InjectMocks DlPromptResolver resolver;

    @Test
    void resolve_returnsCategorySpecificPrompt() {
        WhCategory cat = new WhCategory();
        WhListing listing = WhListing.builder().whCategory(cat).build();
        ItemText item = ItemText.builder().whListing(listing).build();
        DlCategoryPrompt prompt = DlCategoryPrompt.builder()
            .prompt("Welches Laptop?").build();

        when(promptRepo.findByWhCategory(cat)).thenReturn(Optional.of(prompt));

        assertThat(resolver.resolve(item)).isEqualTo("Welches Laptop?");
    }

    @Test
    void resolve_fallsBackToDefault_whenNoCategoryMatch() {
        WhCategory cat = new WhCategory();
        WhListing listing = WhListing.builder().whCategory(cat).build();
        ItemText item = ItemText.builder().whListing(listing).build();
        DlCategoryPrompt defaultPrompt = DlCategoryPrompt.builder()
            .prompt("Default Prompt").build();

        when(promptRepo.findByWhCategory(cat)).thenReturn(Optional.empty());
        when(promptRepo.findDefault()).thenReturn(Optional.of(defaultPrompt));

        assertThat(resolver.resolve(item)).isEqualTo("Default Prompt");
    }

    @Test
    void resolve_fallsBackToHardcoded_whenDBEmpty() {
        ItemText item = ItemText.builder().whListing(null).build();

        when(promptRepo.findDefault()).thenReturn(Optional.empty());

        assertThat(resolver.resolve(item))
            .isEqualTo(DlCategoryPromptDefinitions.DEFAULT);
    }

    @Test
    void resolve_fallsBackToDefault_whenWhListingNull() {
        ItemText item = ItemText.builder().whListing(null).build();
        DlCategoryPrompt defaultPrompt = DlCategoryPrompt.builder()
            .prompt("Default Prompt").build();

        when(promptRepo.findDefault()).thenReturn(Optional.of(defaultPrompt));

        assertThat(resolver.resolve(item)).isEqualTo("Default Prompt");
    }
}
```

---

## Prompt F – ExtractionModel: Interface, Abstract, Gelectra

*Setzt Prompt A voraus. Parallelisierbar mit: Prompt E*

### Aufgabe

1. `ExtractionModel` Interface anlegen
2. `AbstractExtractionModel` anlegen
3. `GelectraExtractionModel` als erste Implementierung
4. `application.yml` mit Modell-Konfiguration ergänzen

### Interface

```java
public interface ExtractionModel {
    // Einzige Verbindung zur DlModelConfig in DB
    // getName() muss exakt mit DlModelConfig.modelName übereinstimmen
    String getName();

    // maxTokens kommt von außen aus DlModelConfig – nicht hardcodiert
    // modelVersion kommt aus DB – kein getVersion() nötig
    List<ExtractionResult> extract(ItemText input, String prompt, int maxTokens);
}
```

### Abstract

```java
public abstract class AbstractExtractionModel implements ExtractionModel {
    protected final HuggingFaceTokenizer tokenizer;

    protected String buildContext(ItemText input) {
        return input.getTitle() + "\n\n" + input.getDescription();
    }

    // Kürzen auf modellspezifisches Token-Limit via DJL Tokenizer
    protected String truncate(String text) {
        return tokenizer.encode(text, true).toString();
    }

    protected abstract Predictor<?, ?> loadModel();
}
```

### Implementierungen

Jedes Modell braucht eine eigene `@Component`-Klasse. Spring lädt keine Implementierungen
automatisch aus DB-Einträgen – die DB steuert nur welche Modelle **aktiv** sind.
Wenn ein DB-Eintrag keinen passenden `@Component` hat, wird er im Orchestrator übersprungen.

```java
// @Component ohne Namen – Spring injiziert alle via List<ExtractionModel>
// getName() ist einzige Verbindung zu DlModelConfig.modelName in DB
// modelVersion + maxTokens kommen aus DB, nicht aus dieser Klasse

@Component
public class GelectraExtractionModel extends AbstractExtractionModel {

    public String getName() { return "gelectra-base-germanquad"; }

    public List<ExtractionResult> extract(ItemText input, String prompt, int maxTokens) {
        String context = truncate(buildContext(input), maxTokens);
        // DJL QAInput + Predictor
        // Criteria mit source/localPath aus DlModelConfig
    }
}

@Component
public class BertMultiGermanExtractionModel extends AbstractExtractionModel {

    public String getName() { return "bert-multi-english-german-squad2"; }

    public List<ExtractionResult> extract(ItemText input, String prompt, int maxTokens) {
        String context = truncate(buildContext(input), maxTokens);
        // Analog zu Gelectra – ONNX via onnx-community/bert-multi-english-german-squad2-ONNX
    }
}

// Später – LLM-Migrationspfad:
@Component
public class LlamaExtractionModel extends AbstractExtractionModel {
    public String getName() { return "llama3.2:3b"; }
    // maxTokens kommt aus DB (z.B. 4096) – keine Änderung am Interface nötig
}
```

**Modell-Loading** (lokal, ONNX via Microsoft ORT):

DJL's `Criteria.loadModel()` hat keinen eingebauten QA-Translator für ONNX.
Stattdessen wird direkt `OrtSession` (Microsoft ONNX Runtime) verwendet:

```java
// AbstractExtractionModel – Tokenizer + Session laden
protected void initTokenizer() throws IOException {
    this.tokenizer = HuggingFaceTokenizer.newInstance(
        getModelDir().resolve("tokenizer.json"));
}

protected void initSession() throws Exception {
    this.env = OrtEnvironment.getEnvironment();
    this.session = env.createSession(
        getModelDir().toAbsolutePath().resolve("model.onnx").toString());
}

// QA-Inferenz: Tokenize → ONNX Run → Answer-Span aus Start/End-Logits
protected List<ExtractionResult> runQaInference(String question, String context) {
    Encoding encoding = tokenizer.encode(question, context);
    // input_ids, attention_mask, token_type_ids → OrtSession.run()
    // → start_logits, end_logits → argmax → tokenizer.decode() → ExtractionResult
}
```

Modell-Verzeichnis: `src/main/resources/models/<model-name>/` mit `tokenizer.json` + `model.onnx`.

---

## Prompt G – DlExtractionService, DlFilterService, DlPersistenceService

*Setzt Prompt C + F voraus*

### Aufgabe

1. `DlFilterService` – Ergebnisse filtern (confidence sortieren → top-k → min-confidence)
2. `DlPersistenceService` – Terms speichern, Status setzen, allDone-Check, Event publizieren
3. `DlExtractionService` – Einzelner Run: PENDING setzen, extract(), filter(), save()

### DlFilterService

```java
@Service
public class DlFilterService {

    public List<ExtractionResult> filter(List<ExtractionResult> raw, DlConfig config) {
        return raw.stream()
            .sorted(Comparator.comparingDouble(ExtractionResult::getConfidence).reversed())
            .limit(config.getTopK())
            .filter(r -> r.getConfidence() >= config.getMinConfidence())
            .toList();
    }
}
```

### DlPersistenceService

```java
@Service
@RequiredArgsConstructor
public class DlPersistenceService {

    private final DlExtractionTermRepository termRepo;
    private final DlExtractionRunRepository runRepo;
    private final ApplicationEventPublisher eventPublisher;

    public void saveResults(DlExtractionRun run, List<ExtractionResult> filtered) {
        filtered.forEach(r -> termRepo.save(DlExtractionTerm.builder()
            .run(run).term(r.getTerm())
            .confidence(r.getConfidence()).build()));

        run.setStatus(ExtractionStatus.DONE);
        run.setExtractedAt(LocalDateTime.now());
        runRepo.save(run);

        checkAllDone(run.getItemText());
    }

    private void checkAllDone(ItemText itemText) {
        // DB-Fallback: fängt Restart-Szenarien ab wenn allOf() nicht greift
        // NO_IMPLEMENTATION ist terminal – blockiert allDone nicht
        boolean allDone = runRepo.findByItemText(itemText).stream()
            .allMatch(r -> r.getStatus() == DONE
                        || r.getStatus() == FAILED
                        || r.getStatus() == NO_IMPLEMENTATION);
        if (allDone)
            eventPublisher.publishEvent(new DlExtractionCompletedEvent(itemText.getId()));
    }
}
```

### DlExtractionService

```java
@Service
@RequiredArgsConstructor
public class DlExtractionService {

    private final List<ExtractionModel> models;
    private final DlFilterService filterService;
    private final DlPersistenceService persistenceService;
    private final DlExtractionRunRepository runRepo;
    private final DlConfig config;

    public void runModel(DlExtractionRun run) {
        run.setStatus(ExtractionStatus.PENDING);
        runRepo.save(run);

        try {
            ExtractionModel model = models.stream()
                .filter(m -> m.getName().equals(run.getModelName()))
                .findFirst().orElseThrow();

            int maxTokens = run.getModelConfig().getMaxTokens();
            List<ExtractionResult> raw = model.extract(
                run.getItemText(), run.getPrompt(), maxTokens);
            List<ExtractionResult> filtered = filterService.filter(raw, config);
            persistenceService.saveResults(run, filtered);

        } catch (Exception e) {
            run.setStatus(ExtractionStatus.FAILED);
            run.setErrorMessage(truncateError(e));
            runRepo.save(run);
            log.error("Extraction failed for run {}", run.getId(), e);
        }
    }

    private String truncateError(Exception e) {
        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
        return msg.length() > 500 ? msg.substring(0, 497) + "..." : msg;
    }
}
```

### Unit Tests: DlPersistenceService – allDone Edge Cases

```java
@ExtendWith(MockitoExtension.class)
class DlPersistenceServiceTest {

    @Mock DlExtractionTermRepository termRepo;
    @Mock DlExtractionRunRepository runRepo;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks DlPersistenceService service;

    @Test
    void checkAllDone_firesEvent_whenAllRunsDone() {
        ItemText item = new ItemText();
        List<DlExtractionRun> runs = List.of(
            runWithStatus(DONE), runWithStatus(DONE));
        when(runRepo.findByItemText(item)).thenReturn(runs);

        service.checkAllDone(item);

        verify(eventPublisher).publishEvent(any(DlExtractionCompletedEvent.class));
    }

    @Test
    void checkAllDone_firesEvent_whenMixOfDoneAndFailed() {
        ItemText item = new ItemText();
        when(runRepo.findByItemText(item)).thenReturn(List.of(
            runWithStatus(DONE), runWithStatus(FAILED)));

        service.checkAllDone(item);

        verify(eventPublisher).publishEvent(any(DlExtractionCompletedEvent.class));
    }

    @Test
    void checkAllDone_firesEvent_whenNoImplementationIsTerminal() {
        // NO_IMPLEMENTATION darf allDone nicht blockieren
        ItemText item = new ItemText();
        when(runRepo.findByItemText(item)).thenReturn(List.of(
            runWithStatus(DONE), runWithStatus(NO_IMPLEMENTATION)));

        service.checkAllDone(item);

        verify(eventPublisher).publishEvent(any(DlExtractionCompletedEvent.class));
    }

    @Test
    void checkAllDone_doesNotFireEvent_whenPendingRunExists() {
        ItemText item = new ItemText();
        when(runRepo.findByItemText(item)).thenReturn(List.of(
            runWithStatus(DONE), runWithStatus(PENDING)));

        service.checkAllDone(item);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void checkAllDone_doesNotFireEvent_whenInitRunExists() {
        // INIT-Run blockiert – könnte noch starten
        ItemText item = new ItemText();
        when(runRepo.findByItemText(item)).thenReturn(List.of(
            runWithStatus(DONE), runWithStatus(INIT)));

        service.checkAllDone(item);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private DlExtractionRun runWithStatus(ExtractionStatus status) {
        return DlExtractionRun.builder().status(status).build();
    }
}
```

### Unit Tests: DlFilterService

```java
class DlFilterServiceTest {

    DlFilterService service = new DlFilterService();

    private DlConfig configWith(int topK, double minConfidence) {
        DlConfig c = new DlConfig();
        c.setTopK(topK);
        c.setMinConfidence(minConfidence);
        return c;
    }

    private ExtractionResult result(String term, double confidence) {
        return ExtractionResult.builder().term(term).confidence(confidence).build();
    }

    @Test
    void filter_sortsByConfidenceDesc() {
        List<ExtractionResult> raw = List.of(
            result("A", 0.5), result("B", 0.9), result("C", 0.7));

        List<ExtractionResult> filtered = service.filter(raw, configWith(10, 0.0));

        assertThat(filtered).extracting(ExtractionResult::getTerm)
            .containsExactly("B", "C", "A");
    }

    @Test
    void filter_respectsTopK() {
        List<ExtractionResult> raw = List.of(
            result("A", 0.9), result("B", 0.8), result("C", 0.7));

        assertThat(service.filter(raw, configWith(2, 0.0))).hasSize(2);
    }

    @Test
    void filter_respectsMinConfidence() {
        List<ExtractionResult> raw = List.of(
            result("A", 0.9), result("B", 0.4), result("C", 0.7));

        List<ExtractionResult> filtered = service.filter(raw, configWith(10, 0.6));

        assertThat(filtered).extracting(ExtractionResult::getTerm)
            .containsExactlyInAnyOrder("A", "C");
    }

    @Test
    void filter_returnsEmpty_whenAllBelowMinConfidence() {
        List<ExtractionResult> raw = List.of(result("A", 0.2), result("B", 0.3));

        assertThat(service.filter(raw, configWith(10, 0.8))).isEmpty();
    }
}
```

### Unit Tests: DlExtractionService – Fehlerbehandlung

```java
@ExtendWith(MockitoExtension.class)
class DlExtractionServiceTest {

    @Mock List<ExtractionModel> models;
    @Mock DlFilterService filterService;
    @Mock DlPersistenceService persistenceService;
    @Mock DlExtractionRunRepository runRepo;
    @InjectMocks DlExtractionService service;

    @Test
    void runModel_setsStatusFailed_onException() {
        ExtractionModel model = mock(ExtractionModel.class);
        when(model.getName()).thenReturn("gelectra-base-germanquad");
        when(model.extract(any(), any())).thenThrow(new RuntimeException("DJL error"));
        when(models.stream()).thenReturn(Stream.of(model));

        DlExtractionRun run = DlExtractionRun.builder()
            .modelName("gelectra-base-germanquad")
            .status(ExtractionStatus.INIT)
            .build();

        service.runModel(run);

        assertThat(run.getStatus()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("DJL error");
        verify(runRepo, atLeastOnce()).save(run);
    }

    @Test
    void runModel_errorMessage_truncatedTo500Chars() {
        String longMessage = "x".repeat(600);
        ExtractionModel model = mock(ExtractionModel.class);
        when(model.getName()).thenReturn("gelectra-base-germanquad");
        when(model.extract(any(), any())).thenThrow(new RuntimeException(longMessage));
        when(models.stream()).thenReturn(Stream.of(model));

        DlExtractionRun run = DlExtractionRun.builder()
            .modelName("gelectra-base-germanquad")
            .status(ExtractionStatus.INIT)
            .build();

        service.runModel(run);

        assertThat(run.getErrorMessage()).hasSizeLessThanOrEqualTo(500);
    }
}
```

---

## Prompt H – DlOrchestrationService

*Setzt Prompt D + E + F + G voraus*

### Aufgabe

`DlOrchestrationService` implementieren – Koordination aller Schritte vor dem Extract.

```java
@Service
@RequiredArgsConstructor
public class DlOrchestrationService {

    private final List<ExtractionModel> models;
    private final DlExtractionRunRepository runRepo;
    private final DlPromptResolver promptResolver;
    private final DlExtractionService extractionService;
    private final DlConfig config;

    public void scheduleExtraction(ItemText itemText) {
        String prompt = promptResolver.resolve(itemText);
        String inputHash = sha256(itemText.getTitle() + itemText.getDescription());

        List<CompletableFuture<Void>> futures = modelConfigRepo.findByActiveTrue().stream()
            .filter(mc -> !runRepo.existsByItemTextAndModelConfigAndStatus(
                itemText, mc, ExtractionStatus.DONE))
            .map(mc -> {
                // Component-Check VOR Run anlegen – verhindert blockierende INIT-Runs
                Optional<ExtractionModel> model = models.stream()
                    .filter(m -> m.getName().equals(mc.getModelName()))
                    .findFirst();

                DlExtractionRun run = runRepo.save(DlExtractionRun.builder()
                    .itemText(itemText)
                    .modelConfig(mc)
                    .prompt(prompt)
                    .inputHash(inputHash)
                    .status(model.isPresent()
                        ? ExtractionStatus.INIT
                        : ExtractionStatus.NO_IMPLEMENTATION)  // terminal, blockiert nicht
                    .build());

                return model.map(m ->
                    CompletableFuture.runAsync(() -> extractionService.runModel(run)))
                    .orElse(CompletableFuture.completedFuture(null)); // sofort fertig
            })
            .toList();

        // allOf: Event wenn alle Futures fertig (DONE/FAILED/NO_IMPLEMENTATION)
        // DB-Fallback in DlPersistenceService fängt Restart-Szenarien ab
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> eventPublisher.publishEvent(
                new DlExtractionCompletedEvent(itemText.getId())));
    }
}
```

### Re-Evaluate Scheduler

Optionaler `@Scheduled`-Job – startet hängende `INIT`-Runs neu und verarbeitet `RE_EVALUATE`:

```java
@Scheduled(fixedDelay = 300_000) // alle 5 Minuten
public void retryStuckRuns() {
    // Hängende INIT-Runs (älter als 5 Minuten) neu starten
    runRepo.findByStatusAndCreatedAtBefore(INIT, LocalDateTime.now().minusMinutes(5))
        .forEach(run -> models.stream()
            .filter(m -> m.getName().equals(run.getModelConfig().getModelName()))
            .findFirst()
            .ifPresent(m -> CompletableFuture.runAsync(
                () -> extractionService.runModel(run))));

    // RE_EVALUATE: Terms löschen, auf INIT zurücksetzen, neu starten
    runRepo.findByStatus(RE_EVALUATE).forEach(run -> {
        termRepo.deleteByRun(run);
        run.setStatus(ExtractionStatus.INIT);
        runRepo.save(run);
        models.stream()
            .filter(m -> m.getName().equals(run.getModelConfig().getModelName()))
            .findFirst()
            .ifPresent(m -> CompletableFuture.runAsync(
                () -> extractionService.runModel(run)));
    });
}
```

### Re-Evaluate Endpoint (optional)

```java
// POST /api/dl/runs/{id}/re-evaluate
// Setzt Run auf RE_EVALUATE – Scheduler übernimmt Term-Cleanup + Neustart
@PostMapping("/runs/{id}/re-evaluate")
public ResponseEntity<Void> reEvaluate(@PathVariable Long id) {
    DlExtractionRun run = runRepo.findById(id).orElseThrow();
    run.setStatus(ExtractionStatus.RE_EVALUATE);
    runRepo.save(run);
    return ResponseEntity.accepted().build();
}
```

Alternativ: Status direkt in DB setzen → Scheduler pickt beim nächsten Lauf auf.

### Unit Tests: DlOrchestrationService – Edge Cases

```java
@ExtendWith(MockitoExtension.class)
class DlOrchestrationServiceTest {

    @Mock DlModelConfigRepository modelConfigRepo;
    @Mock DlExtractionRunRepository runRepo;
    @Mock DlPromptResolver promptResolver;
    @Mock DlExtractionService extractionService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks DlOrchestrationService service;

    @Test
    void scheduleExtraction_setsNoImplementation_whenComponentMissing() {
        // DB-Eintrag vorhanden, aber kein @Component registriert
        DlModelConfig mc = modelConfig("unknown-model");
        when(modelConfigRepo.findByActiveTrue()).thenReturn(List.of(mc));
        when(runRepo.existsByItemTextAndModelConfigAndStatus(any(), any(), any()))
            .thenReturn(false);
        when(promptResolver.resolve(any())).thenReturn("prompt");
        service.models = List.of(); // kein @Component registriert

        service.scheduleExtraction(new ItemText());

        verify(runRepo).save(argThat(run ->
            run.getStatus() == NO_IMPLEMENTATION));
    }

    @Test
    void scheduleExtraction_skipsDoneRuns() {
        // bereits DONE → kein neuer Run
        DlModelConfig mc = modelConfig("gelectra-base-germanquad");
        when(modelConfigRepo.findByActiveTrue()).thenReturn(List.of(mc));
        when(runRepo.existsByItemTextAndModelConfigAndStatus(any(), eq(mc), eq(DONE)))
            .thenReturn(true);

        service.scheduleExtraction(new ItemText());

        verify(runRepo, never()).save(any());
    }

    @Test
    void scheduleExtraction_doesNotCreateRun_beforeComponentCheck() {
        // Run darf erst nach positivem Component-Check angelegt werden
        DlModelConfig mc = modelConfig("missing-model");
        when(modelConfigRepo.findByActiveTrue()).thenReturn(List.of(mc));
        when(runRepo.existsByItemTextAndModelConfigAndStatus(any(), any(), any()))
            .thenReturn(false);
        when(promptResolver.resolve(any())).thenReturn("prompt");
        service.models = List.of(); // kein @Component

        service.scheduleExtraction(new ItemText());

        // Run wird angelegt aber mit NO_IMPLEMENTATION – nie mit INIT ohne Component
        verify(runRepo).save(argThat(run ->
            run.getStatus() != INIT));
    }

    private DlModelConfig modelConfig(String name) {
        return DlModelConfig.builder().modelName(name).active(true).build();
    }
}
```

---

## Prompt I – SSE Backend + Angular

*Setzt Prompt H voraus*

### Aufgabe

1. Spring Boot SSE-Endpoint anlegen
2. `DlExtractionCompletedEvent` → `SseEmitter` verdrahten
3. Angular: SSE-Event empfangen, Detail-Panel aktualisieren

### Implementiert: globaler SSE-Hub (ersetzt per-Item SseEmitter)

**Backend**: `SseHub` verwaltet eine `ConcurrentHashMap<eventSourceId, SseEmitter>`.
Jeder Angular-App-Instance verbindet einmalig beim Start mit einer UUID (`GET /api/sse?eventSourceId=…`).

**SSE-Event**:

| Feld       | Wert                        |
| ---------- | --------------------------- |
| Event-Name | `dl-extract`                |
| Payload    | `DlExtractionDonePayload`   |

```json
{ "itemTextId": 42 }
```

`DlExtractionDonePayload` ist ein Backend-DTO in `at.querchecker.dto` und wird via OpenAPI
generiert (`@ApiResponse(schema = @Schema(oneOf = ...))` am `SseController`).

**Extraktionsergebnisse** werden nicht im SSE-Payload mitgeliefert — der Frontend holt sie
nach Empfang des Events via `GET /api/dl/extraction/{itemTextId}/terms`:

```json
[
  { "modelName": "gelectra-base-germanquad", "term": "ThinkPad X1 Carbon Gen 11", "confidence": 0.94 },
  { "modelName": "bert-multi-english-german-squad2", "term": "Lenovo ThinkPad X1", "confidence": 0.81 }
]
```

**Angular**: `EventSourceServerService<AppSseEventName, DlExtractionDonePayload>` ist ein
`providedIn: 'root'`-Singleton. `item-research.component.ts` registriert sich auf `dl-extract`
und filtert nach `payload.itemTextId === detail().itemTextId`, dann `GET /api/dl/extraction/{itemTextId}/terms`.

---

## Prompt J – Evaluierung & Produktivstellung

*Setzt alle vorherigen Schritte voraus*

### Aufgabe

1. DB-Vergleich der Modell-Ergebnisse
2. Gewähltes Modell herunterladen
3. `application.yml` auf `source: LOCAL` umstellen

### Evaluierungs-Query

```sql
-- Vergleich der Modelle für dasselbe Item
-- (DlExtractionRun hat FK auf dl_model_config, nicht mehr model_name direkt)
SELECT
    mc.model_name,
    mc.model_version,
    t.term,
    t.confidence
FROM dl_extraction_term t
JOIN dl_extraction_run r  ON t.run_id = r.id
JOIN dl_model_config mc   ON r.model_config_id = mc.id
WHERE r.item_text_id = :itemTextId
ORDER BY mc.model_name, t.confidence DESC;

-- Durchschnittliche Confidence pro Modell
SELECT mc.model_name, AVG(t.confidence) as avg_confidence, COUNT(*) as term_count
FROM dl_extraction_term t
JOIN dl_extraction_run r  ON t.run_id = r.id
JOIN dl_model_config mc   ON r.model_config_id = mc.id
GROUP BY mc.model_name
ORDER BY avg_confidence DESC;
```

### Produktivstellung

Modell-Konfiguration via DB-Update (nicht mehr in `application.yml`):

```sql
-- Gewähltes Modell auf LOCAL umstellen
UPDATE dl_model_config
SET source = 'LOCAL', local_path = '/models/gelectra-base-germanquad'
WHERE model_name = 'gelectra-base-germanquad';

-- Nicht gewähltes Modell deaktivieren
UPDATE dl_model_config SET active = false
WHERE model_name = 'bert-multi-english-german-squad2';
```

Filter-Konfiguration in `application.yml` anpassen:

```yaml
querchecker:
  dl:
    min-confidence: 0.65
    top-k: 3
```

---

## Fortschritt

### v0.1.0 – Design (abgeschlossen)

- [x] Entity-Design
- [x] Architektur & Service-Verantwortlichkeiten
- [x] Beispiel-Prompts je Hauptkategorie

### Offene TODOs (außerhalb dieses Dokuments)

- [x] **TODO**: Projektwissen (`memory/backend.md`) aktualisiert:
  Enum-Konvention ergänzt: `@Enumerated(EnumType.STRING)` statt nativer PG-Enums.

### v0.1.0 – Implementierung

- [x] **Prompt A**: Maven + Flyway Setup
- [x] **Prompt B**: DlCategoryPrompt (parallel mit C)
- [x] **Prompt C**: Restliche Entities + Migrations (parallel mit B)
- [x] **Prompt D**: ItemText-Erstellung in WhSearchService
- [x] **Prompt E**: DlPromptResolver (parallel mit F)
- [x] **Prompt F**: ExtractionModel Interface + Gelectra + BertMultiGerman (parallel mit E)
- [x] **Prompt G**: DlExtractionService, DlFilterService, DlPersistenceService
- [x] **Prompt H**: DlOrchestrationService (inkl. Re-Evaluate Scheduler + optionalem Endpoint)
- [x] **Prompt I**: SSE Backend + Angular
- [ ] **Prompt J**: Evaluierung + Produktivstellung
