# anatomy.md

> Auto-maintained by OpenWolf. Last scanned: 2026-07-08T19:04:28.266Z
> Files: 615 tracked | Anatomy hits: 0 | Misses: 0

## ../../../.claude/projects/-home-leo-programming-github-querchecker/memory/

- `backend.md` — Backend Details (~7706 tok)
- `MEMORY.md` — Querchecker Project Memory (~4750 tok)
- `todo_readme_doccheck.md` (~203 tok)
- `user.md` — Technical Profile (~345 tok)

## ../../../Downloads/

- `leo-bio-raw.md` — Leo — Bio Raw Material (~5315 tok)

## ./

- `.gitignore` — Git ignore rules (~234 tok)
- `CLAUDE.md` — OpenWolf (~357 tok)
- `docker-compose.prod.yml` — Docker Compose: 1 services (~448 tok)
- `docker-compose.yml` — Docker Compose services (~77 tok)
- `keybert-test.py` — Your test data (Short German Willhaben-style text) (~301 tok)
- `README.md` — Project documentation (~3075 tok)

## .claude/

- `settings.json` (~441 tok)
- `settings.local.json` — Declares v (~5095 tok)

## .claude/rules/

- `openwolf.md` (~313 tok)

## .zed/

- `prompts.md` — Frontend Styling Prompts (~352 tok)
- `settings.json` — Folder-specific settings (~162 tok)
- `tasks.json` (~880 tok)

## .zed/prompts/project-knowledge/

- `backend.md` — Projektwissen: Querchecker — Backend (~7800 tok)
- `frontend.md` — Projektwissen: Querchecker — Frontend (~4871 tok)
- `general.md` — Projektwissen: Querchecker — Allgemein (~1086 tok)

## backend/

- `.classpath` (~614 tok)
- `.factorypath` (~6176 tok)
- `.project` (~226 tok)
- `Dockerfile` — Docker container definition (~84 tok)
- `pom.xml` — /*.onnx</exclude> (~1553 tok)
- `spotbugs-exclude.xml` — Declares alone (~374 tok)

## backend/.settings/

- `org.eclipse.core.resources.prefs` (~40 tok)
- `org.eclipse.jdt.apt.core.prefs` (~58 tok)
- `org.eclipse.jdt.core.prefs` (~144 tok)
- `org.eclipse.m2e.core.prefs` (~23 tok)

## backend/src/main/java/at/querchecker/

- `QuercheckerApplication.java` — QuercheckerApplication: main (~227 tok)

## backend/src/main/java/at/querchecker/api/config/

- `ApiRestClientConfig.java` — Erstellt vorkonfigurierte RestClient-Beans für LLM-Provider (Groq, OpenRouter). (~795 tok)
- `FreeLimitPeriod.java` — Class: FreeLimitPeriod (~26 tok)
- `LimitUnit.java` — Class: LimitUnit (~25 tok)
- `LlmMode.java` — Class: LlmMode (~23 tok)
- `LlmProperties.java` — Zentrale LLM-Konfiguration. (~468 tok)
- `ProviderConfig.java` — Konfiguration für einen einzelnen API-Provider. (~310 tok)
- `ProviderProperties.java` — API-Provider-Konfiguration. (~382 tok)

## backend/src/main/java/at/querchecker/api/entity/

- `ApiUsageLog.java` — LLM-Modellname (z.B. "llama-3.1-8b-instant"), null für Such-Provider (~298 tok)
- `Provider.java` — Provider: getDisplayName (~112 tok)
- `RequestType.java` — Class: RequestType (~36 tok)

## backend/src/main/java/at/querchecker/api/exception/

- `RateLimitException.java` — Thrown when an API provider returns HTTP 429 Too Many Requests. (~394 tok)

## backend/src/main/java/at/querchecker/api/extraction/

- `AbstractLlmExtractionClient.java` — Gemeinsame Logik für LLM-basierte ExtractionClient-Implementierungen (~6942 tok)
- `ExtractionClient.java` — Provider-unabhängiges Interface für LLM-Extraktion. (~658 tok)
- `ExtractionProviderRouter.java` — Leitet Extraction-Calls an den konfigurierten aktiven Provider weiter. (~246 tok)
- `GroqExtractionClient.java` — ExtractionClient-Implementierung für Groq (llama-3.x). (~630 tok)
- `OpenRouterExtractionClient.java` — ExtractionClient-Implementierung für OpenRouter. (~475 tok)
- `ProductNameResult.java` — Strukturiertes Ergebnis der PRODUCT_NAME-Extraktion. (~165 tok)

## backend/src/main/java/at/querchecker/api/model/

- `ChatRequest.java` — Request-Body für OpenAI-kompatible Chat-Completion-Endpunkte (Groq, OpenRouter). (~375 tok)
- `ChatResponse.java` — Response-Body von OpenAI-kompatiblen Chat-Completion-Endpunkten (Groq, OpenRouter). (~497 tok)

## backend/src/main/java/at/querchecker/api/repository/

- `ApiUsageLogRepository.java` — Class: ApiUsageLogRepository (~1014 tok)

## backend/src/main/java/at/querchecker/api/result/

- `ApiCallResult.java` — Ergebnis-Typ für externe API-Calls (LLM + Web Search). (~296 tok)

## backend/src/main/java/at/querchecker/api/search/

- `BraveWebSearchService.java` — Brave Search-Implementierung von WebSearchService. (~2940 tok)
- `GoogleDiscoveryWebSearchService.java` — Strips only the locale prefix (e.g. /de/, /us/, /ar-sa/) from the URL path for deduplication — (~2139 tok)
- `SearchProperties.java` — Konfiguration für den aktiven Web-Search-Provider. (~166 tok)
- `SearchProvider.java` — SearchProvider: toProvider (~96 tok)
- `WebSearchProviderRouter.java` — Service: WebSearchProviderRouter (~195 tok)
- `WebSearchService.java` — Quellunabhängiges Interface für Produktsuchen. (~563 tok)

## backend/src/main/java/at/querchecker/api/service/

- `ApiUsageLogService.java` — Protokolliert jeden echten API-Call — nie bei Cache-Hits. (~1079 tok)
- `QuotaService.java` — Prüft und verwaltet API-Kontingente basierend auf ProviderProperties. (~1070 tok)
- `QuotaStatus.java` — Class: QuotaStatus (~27 tok)

## backend/src/main/java/at/querchecker/auth/

- `AccessKey.java` — Entity: AccessKey (~320 tok)
- `AccessKeyController.java` — RestController: AccessKeyController (7 endpoints) (~601 tok)
- `AccessKeyRepository.java` — Class: AccessKeyRepository (~72 tok)
- `AccessKeyService.java` — Service: AccessKeyService (~908 tok)
- `AccessKeyUsage.java` — Ebene-2-Kontingent: zählt Nutzeraktionen (Spec-Lookups) pro Key und Tag. (~350 tok)
- `AccessKeyUsageRepository.java` — Verbleibendes Tageskontingent des Keys in einer Query — die DB rechnet, (~689 tok)
- `AccessKeyUsageService.java` — Ebene-2-Kontingent (Key-Kontingent) aus dem Berechtigungskonzept, Kap. 4. (~936 tok)
- `AuthController.java` — RestController: AuthController (4 endpoints) (~327 tok)
- `AuthProperties.java` — DSGVO-Retention für Key-Nutzungshistorie (access_key_usage), Konzept Kap. 7. (~130 tok)
- `AuthService.java` — Service: AuthService (~1164 tok)
- `LocalProfileAuthFilter.java` — Component: LocalProfileAuthFilter (~455 tok)
- `QuerCheckerPrincipal.java` — accessKeyId der aktuellen USER-Session für Traceability-Zwecke (nicht Kontingent-Buchung, (~323 tok)
- `QuotaExceededException.java` — Ebene-2-Kontingent des Keys für den heutigen Tag erschöpft. (~148 tok)
- `Role.java` — Class: Role (~56 tok)
- `SessionCookieAuthFilter.java` — Component: SessionCookieAuthFilter (~986 tok)
- `UserSession.java` — Entity: UserSession (~240 tok)
- `UserSessionCleanupScheduler.java` — Component: UserSessionCleanupScheduler (~295 tok)
- `UserSessionRepository.java` — Class: UserSessionRepository (~105 tok)

## backend/src/main/java/at/querchecker/auth/dto/

- `AccessKeyCreatedDto.java` — Class: AccessKeyCreatedDto (~89 tok)
- `AccessKeyOverviewDto.java` — Class: AccessKeyOverviewDto (~83 tok)
- `AuthStatusDto.java` — Class: AuthStatusDto (~123 tok)
- `LoginResponseDto.java` — Class: LoginResponseDto (~31 tok)

## backend/src/main/java/at/querchecker/config/

- `AsyncConfig.java` — Configuration: AsyncConfig (~61 tok)
- `CorsConfig.java` — Configuration: CorsConfig (~231 tok)
- `HttpClientProperties.java` — HTTP client timeout configuration for external API calls (~169 tok)
- `ProviderSetupService.java` — Baut die Datenstruktur für den Einrichtungs-Assistenten. (~4565 tok)
- `ProviderState.java` — Status eines externen Providers. (~243 tok)
- `ProviderStatus.java` — SSE-Payload und REST-Antwort für den aktuellen Provider-Status. (~208 tok)
- `ProviderStatusService.java` — Verwaltet den Laufzeit-Status aller externen Provider (Web Search + LLM). (~1990 tok)
- `ProviderTestService.java` — Führt minimale Verbindungstests für aktive Provider durch. (~436 tok)
- `RequestUserAgentResolver.java` — Löst den User-Agent für ausgehende HTTP-Requests auf. (~335 tok)
- `RestTemplateConfig.java` — Configuration: RestTemplateConfig (~169 tok)
- `SecurityConfig.java` — Configuration: SecurityConfig (~894 tok)
- `SpringDocConfig.java` — Replaces the default PolymorphicModelConverter with a cycle-breaking pass-through. (~704 tok)
- `UserAgentFilter.java` — Component: UserAgentFilter (~266 tok)
- `UserAgentHolder.java` — Speichert den User-Agent des ersten Browser-Requests. (~250 tok)
- `YamlCommentParser.java` — Parst YAML-Dateien und extrahiert Inline-Kommentare pro Schlüssel-Pfad. (~864 tok)

## backend/src/main/java/at/querchecker/controller/

- `AdminController.java` — Admin-Endpoints für Server-Verwaltung. (~497 tok)
- `ApiUsageController.java` — RestController: ApiUsageController (2 endpoints) (~1452 tok)
- `HealthController.java` — RestController: HealthController (1 endpoints) (~101 tok)
- `ProviderSetupController.java` — Endpoints für den Einrichtungs-Assistenten. (~1318 tok)
- `ProviderStatusController.java` — Gibt den aktuellen Provider-Status zurück. (~530 tok)
- `SettingsPreferencesController.java` — RestController: SettingsPreferencesController (3 endpoints) (~687 tok)
- `WhListingController.java` — RestController: WhListingController (6 endpoints) (~578 tok)
- `WhListingDetailController.java` — RestController: WhListingDetailController (5 endpoints) (~594 tok)

## backend/src/main/java/at/querchecker/controller/dto/

- `ModelUsageDto.java` — Class: ModelUsageDto (~74 tok)
- `PreferenceRequest.java` — Class: PreferenceRequest (~47 tok)
- `PreferenceResponse.java` — Class: PreferenceResponse (~80 tok)
- `ProviderSetupInitResponse.java` — Antwort von GET /api/provider-setup/init. (~193 tok)
- `ProviderSetupKeysResponse.java` — Antwort von GET /api/provider-setup/keys?provider=X. (~264 tok)
- `ProviderSetupSaveRequest.java` — Request-Body für POST /api/provider-setup/save. (~216 tok)
- `ProviderUsageDto.java` — Modellname — nur bei LLM-Providern (Groq, OpenRouter), sonst null (~212 tok)
- `SetupDimensionDto.java` — Eine Konfigurations-Dimension im Einrichtungs-Assistenten. (~176 tok)
- `SetupFieldDto.java` — Ein einzelnes Konfigurationsfeld im Einrichtungs-Assistenten. (~236 tok)
- `SetupProviderDto.java` — Ein Provider im Einrichtungs-Assistenten (z.B. BRAVE, GROQ). (~128 tok)
- `UsageResponse.java` — Aktiver Web-Search-Provider: BRAVE | GOOGLE_DISCOVERY (~186 tok)

## backend/src/main/java/at/querchecker/deepLearning/

- `DlCategoryPromptDefinitions.java` — Zentrale Definition aller Kategorie-Prompts. (~2148 tok)
- `DlExtractionCompletedEvent.java` — String, nicht {@link ExtractionStatus} — Transportwert für SSE, entkoppelt von der (~173 tok)
- `ExtractionResult.java` — Class: ExtractionResult (~114 tok)
- `ExtractionStatus.java` — Class: ExtractionStatus (~49 tok)
- `HtmlUtils.java` — Utility for extracting plain text from HTML content. (~169 tok)
- `ItemSource.java` — Class: ItemSource (~23 tok)
- `ModelSource.java` — Class: ModelSource (~30 tok)
- `TokenAnalyzer.java` — TokenAnalyzer: processText (~365 tok)

## backend/src/main/java/at/querchecker/deepLearning/config/

- `DlConfig.java` — Component: DlConfig (~162 tok)
- `DlModelConfiguration.java` — Conditionally registers ExtractionModel singletons based on: (~1576 tok)

## backend/src/main/java/at/querchecker/deepLearning/controller/

- `DlExtractionController.java` — Returns extraction terms + overall status for a whItemId. (~1821 tok)
- `DlSettingsController.java` — RestController: DlSettingsController (3 endpoints) (~225 tok)

## backend/src/main/java/at/querchecker/deepLearning/entity/

- `DlCategoryPrompt.java` — Entity: DlCategoryPrompt (~295 tok)
- `DlExtractionRun.java` — Entity: DlExtractionRun (~442 tok)
- `DlExtractionTerm.java` — Entity: DlExtractionTerm (~201 tok)
- `DlModelConfig.java` — Entity: DlModelConfig (~240 tok)
- `ItemText.java` — Entity: ItemText (~269 tok)
- `PromptType.java` — Class: PromptType (~88 tok)

## backend/src/main/java/at/querchecker/deepLearning/extraction/

- `AbstractExtractionModel.java` — Override to false for models that do not accept token_type_ids (e.g. DeBERTa-v3). (~2404 tok)
- `AbstractLlamaExtractionModel.java` — Base class for GGUF-based generative extraction models (llama.cpp via java-llama.cpp). (~1654 tok)
- `ExtractionModel.java` — Class: ExtractionModel (~164 tok)
- `Llama32ExtractionModel.java` — meta-llama/Llama-3.2-3B-Instruct — instruction-following LLM for product name extraction. (~828 tok)
- `LlmApiExtractionModel.java` — Extraction model that delegates to the configured LLM API provider (e.g. Groq). (~683 tok)
- `MdebertaExtractionModel.java` — mDeBERTa-v3 does not use token_type_ids. (~560 tok)
- `NuExtract15ExtractionModel.java` — numind/NuExtract-v1.5 — structured extraction LLM (Qwen2-1.5B fine-tune). (~730 tok)
- `NuExtractExtractionModel.java` — numind/NuExtract-v1.5-tiny — structured extraction LLM (Qwen2-0.5B fine-tune). (~917 tok)
- `Qwen25ExtractionModel.java` — Qwen/Qwen2.5-3B-Instruct — instruction-following LLM for product name extraction. (~779 tok)

## backend/src/main/java/at/querchecker/deepLearning/repository/

- `DlCategoryPromptRepository.java` — Class: DlCategoryPromptRepository (~257 tok)
- `DlExtractionRunRepository.java` — Class: DlExtractionRunRepository (~486 tok)
- `DlExtractionTermRepository.java` — Class: DlExtractionTermRepository (~552 tok)
- `DlModelConfigRepository.java` — Class: DlModelConfigRepository (~100 tok)
- `ItemTextRepository.java` — Class: ItemTextRepository (~376 tok)

## backend/src/main/java/at/querchecker/deepLearning/service/

- `DlCategoryPromptSeeder.java` — Befüllt DlCategoryPrompt per Upsert beim Start. (~1408 tok)
- `DlExtractionService.java` — Service: DlExtractionService (~1523 tok)
- `DlFilterService.java` — Service: DlFilterService (~184 tok)
- `DlOrchestrationService.java` — Rate-Limit (Burst-Schutz, 2s) + Tagesvolumen (Kosten-Schutz, 5x Lookup-Kontingent) für (~3345 tok)
- `DlPersistenceService.java` — Service: DlPersistenceService (~692 tok)
- `DlPromptResolver.java` — Löst Prompt auf: Eigene Kategorie → Eltern → ... → Default (whCategory=null). (~653 tok)
- `ExtractionTask.java` — ExtractionTask: run, getRun (~135 tok)
- `ItemTextCleanupScheduler.java` — Löscht alte ItemText-Records die: (~351 tok)
- `ItemTextService.java` — Strategy: bei Inhaltsänderung neuer Record – kein Update. (~766 tok)
- `KeywordExtractionService.java` — KeywordExtractionService: processText (~699 tok)

## backend/src/main/java/at/querchecker/dto/

- `DlExtractionDonePayload.java` — Best term from the configured source model — pre-fills the spec-lookup field. (~129 tok)
- `DlExtractionStatusResponse.java` — Overall extraction status for this item. (~237 tok)
- `DlExtractionTermDto.java` — Class: DlExtractionTermDto (~99 tok)
- `DlSettingsDto.java` — Class: DlSettingsDto (~74 tok)
- `ListingRefreshedPayload.java` — SSE payload broadcast after an async Willhaben detail refresh. (~106 tok)
- `WhCategoryDto.java` — Class: WhCategoryDto (~134 tok)
- `WhDetailDto.java` — Vollständiges DTO für die Detail-Ansicht eines Inserats. (~405 tok)
- `WhItemDto.java` — Vollständige Willhaben-URL (Prefix + SEO-Pfad). (~330 tok)
- `WhListingDetailDto.java` — Vollständige Beschreibung (live von WH geholt, nicht gespeichert in wh_item). (~200 tok)
- `WhLocationDto.java` — Class: WhLocationDto (~126 tok)
- `WhMetaStatusDto.java` — Class: WhMetaStatusDto (~92 tok)
- `WhPreviewDto.java` — Class: WhPreviewDto (~50 tok)
- `WhSearchResultDto.java` — Gesamtanzahl der Treffer laut Willhaben (kann größer sein als die abgerufene Seitenanzahl). (~92 tok)

## backend/src/main/java/at/querchecker/entity/

- `AppConfig.java` — Entity: AppConfig (~147 tok)
- `WhCategory.java` — Willhaben ATTRIBUTE_TREE-Wert, z.B. 5882 für Grafikkarten. (~240 tok)
- `WhItem.java` — Vom User bestätigter/korrigierter Suchterm für den Spec-Lookup (~333 tok)
- `WhListing.java` — Relativer SEO-Pfad (ohne https://www.willhaben.at/iad/ Prefix). (~495 tok)
- `WhLocation.java` — Willhaben areaId-Wert, z.B. 900 für Wien. (~219 tok)

## backend/src/main/java/at/querchecker/repository/

- `AppConfigRepository.java` — Class: AppConfigRepository (~63 tok)
- `WhCategoryRepository.java` — Class: WhCategoryRepository (~187 tok)
- `WhItemRepository.java` — Repository: WhItemRepository (~426 tok)
- `WhListingRepository.java` — Repository: WhListingRepository (~420 tok)
- `WhLocationRepository.java` — Class: WhLocationRepository (~131 tok)

## backend/src/main/java/at/querchecker/research/

- `CategorySearchSourceService.java` — Liefert aktive, lookup-enabled Quellen für eine Kategorie. (~895 tok)
- `CategorySpecPreferenceService.java` — Verwaltet kategoriespezifische Spezifikations-Präferenzen. (~1147 tok)
- `ExtractionQualityEvaluator.java` — Bewertet die Qualität eines LLM-Extraktionsergebnisses. (~604 tok)
- `HtmlFetchService.java` — Entscheidet ob für einen SourceType ein vollständiger HTML-Fetch gemacht wird. (~979 tok)
- `IcecatService.java` — Lädt vollständige Produktspezifikationen von Icecat (kein LLM-Extrakt). (~1150 tok)
- `LookupHistoryService.java` — Service: LookupHistoryService (~1257 tok)
- `ProductLookupController.java` — RestController: ProductLookupController (4 endpoints) (~2639 tok)
- `ProductLookupService.java` — Orchestriert den vollständigen Spec-Lookup-Ablauf: (~8019 tok)
- `SearchResultCacheService.java` — In-memory cache for web search results. (~350 tok)
- `UrlValidator.java` — Validiert die vom LLM gelieferte sourceUrl gegen die echten Brave-Ergebnisse. (~841 tok)

## backend/src/main/java/at/querchecker/research/entity/

- `CategorySearchSource.java` — Entity: CategorySearchSource (~507 tok)
- `CategorySpecPreference.java` — USER-Felder als editierbare Keywords (für Settings-UI und Brave-Query). (~371 tok)
- `CategorySpecPreferenceField.java` — Entity: CategorySpecPreferenceField (~206 tok)
- `ExtractionQuality.java` — ≥60% SYSTEM-Pflichtfelder extrahiert + icecatId vorhanden (bei ICECAT-Quellen). (~146 tok)
- `FieldSource.java` — Geseedet durch CategorySpecPreferenceSeeder. Generische Spec-Labels (cpu, ram, panel_type). (~303 tok)
- `ListingLookupHistory.java` — Null für SUPERUSER (auch dev/local-profile) und GUEST — kein Kontingent, kein Key. (~343 tok)
- `LookupStatus.java` — Class: LookupStatus (~186 tok)
- `ProductLookup.java` — Gesetzt wenn ein COMPLETE-Ergebnis aus dem Cache bedient wurde (kein Brave-Call). (~589 tok)
- `SourceType.java` — Class: SourceType (~95 tok)

## backend/src/main/java/at/querchecker/research/model/

- `BraveApiResponse.java` — Jackson-DTO für die Antwort der Brave Search Web API. (~249 tok)
- `FullSpecsRequest.java` — Class: FullSpecsRequest (~38 tok)
- `FullSpecsResponse.java` — Class: FullSpecsResponse (~55 tok)
- `IcecatFetchResult.java` — Ergebnis eines Icecat-Spec-Abrufs — unterscheidet "nicht gefunden (404)" von anderen Fehlern. (~154 tok)
- `LenientStringMapDeserializer.java` — Toleranter Deserializer für Map<String, String>: coerciert LLM-Abweichungen vom Schema. (~677 tok)
- `LookupHistoryEntryDto.java` — Class: LookupHistoryEntryDto (~156 tok)
- `LookupRequest.java` — Class: LookupRequest (~37 tok)
- `LookupResponse.java` — Already-cached Icecat full-specs JSON, or null if not yet fetched. (~366 tok)
- `LookupResultPayload.java` — SSE event payload for the "lookup-result" event. (~208 tok)
- `ProductLookupResult.java` — Ergebnis einer ProductLookup-Anfrage — enthält Status, quickFacts und Quellen-Metadaten. (~586 tok)
- `QuickFactsResult.java` — LLM-Extraktionsergebnis: technische Specs + Quellenangaben + optionale Feature-Gruppen (HTML-Fetch-Pfad). (~394 tok)
- `SearchResult.java` — Generisches Suchergebnis — quellunabhängige Abstraktion über BraveResult. (~149 tok)

## backend/src/main/java/at/querchecker/research/repository/

- `CategorySearchSourceRepository.java` — Class: CategorySearchSourceRepository (~211 tok)
- `CategorySpecPreferenceFieldRepository.java` — Class: CategorySpecPreferenceFieldRepository (~86 tok)
- `CategorySpecPreferenceRepository.java` — Class: CategorySpecPreferenceRepository (~121 tok)
- `ListingLookupHistoryRepository.java` — Class: ListingLookupHistoryRepository (~167 tok)
- `ProductLookupRepository.java` — Class: ProductLookupRepository (~123 tok)

## backend/src/main/java/at/querchecker/research/seeder/

- `CategorySearchSourceDefinitions.java` — CategorySearchSourceDefinitions: SourceConfig (~1710 tok)
- `CategorySearchSourceSeeder.java` — Seedet CategorySearchSource-Einträge je Kategorie. (~825 tok)
- `CategorySpecPreferenceSeeder.java` — Seedet SYSTEM-Pflichtfelder je Kategorie. (~1262 tok)

## backend/src/main/java/at/querchecker/service/

- `AppConfigService.java` — Service: AppConfigService (~673 tok)
- `WhItemService.java` — For mutation responses — listing metadata not needed by frontend callers. (~1851 tok)
- `WhListingRefreshService.java` — Fetches fresh listing data (description, images, category) from Willhaben (~1910 tok)
- `WhListingService.java` — Service: WhListingService (~1534 tok)

## backend/src/main/java/at/querchecker/sse/

- `ErrorNotificationPayload.java` — SSE payload for error notifications. (~130 tok)
- `SseController.java` — Opens a persistent SSE connection for the given client instance. (~383 tok)
- `SseEvent.java` — Unified SSE event envelope for all event types. (~170 tok)
- `SseHub.java` — Manages all active SSE client connections. (~1110 tok)

## backend/src/main/java/at/querchecker/willHaben/

- `WhApiClient.java` — Kapselt alle authentifizierten HTTP-Aufrufe zur Willhaben-API. (~1520 tok)
- `WhCategoryService.java` — Gibt den Kategorie-Baum aus der DB zurück (3 Ebenen). (~3255 tok)
- `WhConstants.java` — Class: WhConstants (~76 tok)
- `WhLocationService.java` — Gibt den Standort-Baum aus der DB zurück (Bundesland → Bezirk). (~1884 tok)
- `WhMetaController.java` — RestController: WhMetaController (5 endpoints) (~716 tok)
- `WhRefreshScheduler.java` — Läuft nach dem konfigurierten Cron-Ausdruck (Standard: montags um 03:00). (~1026 tok)
- `WhSearchController.java` — RestController: WhSearchController (2 endpoints) (~532 tok)
- `WhSearchService.java` — Sucht auf Willhaben nach dem angegebenen Keyword, upsertet alle Ergebnisse (~4158 tok)

## backend/src/main/java/at/querchecker/willHaben/api/

- `WhApiResponse.java` — Inoffizielle Willhaben-JSON-API – Response-Strukturen. (~2619 tok)

## backend/src/main/resources/

- `application-prod.yml` (~329 tok)
- `application.yml` (~461 tok)

## backend/src/main/resources/META-INF/

- `spring-devtools.properties` — Load the llama.cpp JNI jar from the base classloader so the native (~115 tok)

## backend/src/main/resources/db/migration/

- `V0_9__create_base_tables.sql` — Pre-create JPA-managed tables that early Flyway migrations reference via FK. (~129 tok)
- `V0_9_1__create_app_config.sql` — app_config has a string PK and NOT NULL columns used by V16's INSERT, (~153 tok)
- `V1__create_dl_category_prompt.sql` — SQL: tables: dl_category_prompt (~114 tok)
- `V10__add_mdeberta_model_config.sql` (~69 tok)
- `V11__drop_term_type_from_dl_extraction_term.sql` — SQL: 1 alter(s) (~16 tok)
- `V12__add_llm_model_configs.sql` (~95 tok)
- `V13__rename_qwen3_to_qwen25.sql` — Qwen3-4B requires llama.cpp with qwen3 arch support (not yet in de.kherud:llama 4.2.0). (~90 tok)
- `V14__replace_nuextract_2_with_1_5_tiny.sql` — Replace NuExtract-2.0-4B (qwen2vl arch, incompatible with raw llama.cpp prompting) (~104 tok)
- `V15__reduce_nuextract_max_tokens.sql` — NuExtract-1.5-tiny only needs to generate the product_name value (~64 tokens max). (~74 tok)
- `V16__add_dl_context_max_tokens.sql` (~62 tok)
- `V17__replace_gelectra_with_nuextract15.sql` — gelectra-large-germanquad offers no meaningful advantage over mdeberta-v3-base-squad2 (~138 tok)
- `V18__add_llama32_deactivate_nuextract.sql` — NuExtract-1.5-tiny (0.5B) is too imprecise for reliable extraction. (~136 tok)
- `V19__add_execution_order_and_duration_ms.sql` — Add execution_order to dl_model_config for sequential, ordered extraction runs. (~370 tok)
- `V2__create_dl_model_config.sql` — SQL: tables: dl_model_config (~202 tok)
- `V20__add_cancelled_status_and_queue_config.sql` — Add CANCELLED to the extraction_status PG enum (~104 tok)
- `V21__add_lookup_term_to_wh_item.sql` — SQL: 1 alter(s) (~22 tok)
- `V22__create_product_lookup.sql` — SQL: tables: product_lookup, product_lookup_source_url (~231 tok)
- `V23__create_category_spec_preference.sql` — SQL: tables: category_spec_preference, category_spec_preference_field (~152 tok)
- `V24__create_api_usage_log.sql` — SQL: tables: api_usage_log (~129 tok)
- `V25__alter_dl_category_prompt_add_prompt_type.sql` — PromptType als VARCHAR (EnumType.STRING-Konvention des Projekts) (~244 tok)
- `V26__add_groq_model_config.sql` — Add Groq API model to dl_model_config. (~116 tok)
- `V27__reseed_product_name_prompts.sql` — Re-seed PRODUCT_NAME prompts with improved system prompt. (~61 tok)
- `V28__reseed_quick_facts_prompts.sql` — Re-seed QUICK_FACTS prompts: clarify icecatId format (numeric ID at end of URL, not product code). (~68 tok)
- `V29__rename_provider_google_to_icecat.sql` (~21 tok)
- `V3__create_item_text.sql` — SQL: tables: item_text (~123 tok)
- `V30__create_category_search_source.sql` — SQL: tables: category_search_source (~435 tok)
- `V31__unified_attribute_architecture.sql` — P2b: Unified Attribute Architecture (~192 tok)
- `V32__extend_product_lookup_source.sql` — P8: Quellen-Tracking für ProductLookup (welche Quelle hat das Ergebnis geliefert) (~159 tok)
- `V33__reseed_quick_facts_prompts.sql` — P8: QUICK_FACTS-Prompts neu befüllen — icecatUrl → sourceUrl (~59 tok)
- `V34__add_feature_groups_json.sql` — P8b: gruppierte Specs für GSMARENA / FlatpanelsHD (HTML-Fetch-Pfad) (~86 tok)
- `V35__enable_source_inheritance_for_tv_category.sql` — Enable source inheritance for Fernseher (TV) category (~83 tok)
- `V36__drop_wh_item_tags_disable_local_models.sql` — Tags-Infrastruktur entfernen (Frontend + Backend-Endpoint bereits entfernt) (~67 tok)
- `V37__add_lookup_ttl_config.sql` — TTL-Konfiguration für gecachte ProductLookup-Einträge (~117 tok)
- `V38__update_dl_extraction_term.sql` — Remove unused user-correction fields, add condensed_specs_json for structured listing specs (~91 tok)
- `V39__add_model_name_to_api_usage_log.sql` — Track which LLM model was used per API call. (~56 tok)
- `V4__create_dl_extraction_run.sql` — SQL: tables: dl_extraction_run (~213 tok)
- `V40__create_listing_lookup_history.sql` — SQL: tables: listing_lookup_history (~182 tok)
- `V41__add_extracted_model_to_dl_extraction_term.sql` — Add extractedModel column to dl_extraction_term (~57 tok)
- `V42__create_access_key.sql` — SQL: tables: access_key (~96 tok)
- `V43__create_user_session.sql` — SQL: tables: user_session (~93 tok)
- `V44__create_access_key_usage.sql` — SQL: tables: access_key_usage (~72 tok)
- `V45__add_access_key_id_to_lookup_and_usage_log.sql` (~48 tok)
- `V46__add_extraction_consumed_count.sql` (~26 tok)
- `V5__create_dl_extraction_term.sql` — SQL: tables: dl_extraction_term (~132 tok)
- `V6__add_wh_category_to_wh_listing.sql` — SQL: 1 alter(s) (~50 tok)
- `V7__rename_model_config_roberta_to_bert_multi.sql` (~43 tok)
- `V8__upgrade_models_to_large.sql` — Upgrade from two base models to single large model for better extraction quality. (~196 tok)
- `V9__extraction_status_to_postgres_enum.sql` — SQL: 3 alter(s) (~120 tok)

## backend/src/main/resources/models/

- `download_llama32.py` — URL configuration (~494 tok)
- `download_mdeberta.py` (~372 tok)
- `download_nuextract.py` — URL configuration (~501 tok)
- `download_nuextract15.py` — URL configuration (~520 tok)
- `download_qwen25.py` — URL configuration (~509 tok)
- `download_qwen3.py` — URL configuration (~446 tok)

## backend/src/main/resources/models/.venv/

- `.gitignore` — Git ignore rules (~19 tok)
- `pyvenv.cfg` (~59 tok)

## backend/src/main/resources/models/.venv/bin/

- `activate` — This file must be used with "source bin/activate" *from bash* (~606 tok)
- `activate.csh` — This file must be used with "source bin/activate.csh" *from csh*. (~259 tok)
- `activate.fish` — This file must be used with "source <venv>/bin/activate.fish" *from fish* (~598 tok)
- `Activate.ps1` — Declares from (~2409 tok)
- `hf` (~72 tok)
- `httpx` (~68 tok)
- `markdown-it` (~72 tok)
- `pip` (~72 tok)
- `pip3` (~72 tok)
- `pip3.13` (~72 tok)
- `pygmentize` (~71 tok)
- `tiny-agents` (~75 tok)
- `tqdm` (~69 tok)
- `typer` (~69 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/

- `typing_extensions.py` — _Sentinel: final, done, done, disjoint_base + 1 more (~45837 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/_yaml/

- `__init__.py` — This is a stub package designed to roughly emulate the _yaml (~401 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/annotated_doc-0.0.4.dist-info/

- `entry_points.txt` (~9 tok)
- `INSTALLER` (~2 tok)
- `METADATA` — Declares attributes (~1751 tok)
- `RECORD` (~232 tok)
- `WHEEL` (~24 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/annotated_doc-0.0.4.dist-info/licenses/

- `LICENSE` — Project license (~290 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/annotated_doc/

- `__init__.py` (~15 tok)
- `main.py` — Doc: hi (~308 tok)
- `py.typed` (~0 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/anyio-4.12.1.dist-info/

- `entry_points.txt` (~10 tok)
- `INSTALLER` (~2 tok)
- `METADATA` (~1140 tok)
- `RECORD` (~1669 tok)
- `top_level.txt` (~2 tok)
- `WHEEL` (~25 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/anyio-4.12.1.dist-info/licenses/

- `LICENSE` — Project license (~288 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/anyio/

- `__init__.py` — Declares as (~1763 tok)
- `from_thread.py` — _BlockingAsyncContextManager: run, run_sync, run_async_cm, started + 9 more (~5469 tok)
- `functools.py` — _InitialMissingType: cache_info, cache_parameters, cache_clear, cache_info + 12 more (~3136 tok)
- `lowlevel.py` — View: get, get, get (~1474 tok)
- `py.typed` (~0 tok)
- `pytest_plugin.py` — FreePortFactory: extract_backend_and_options, get_runner, pytest_addoption, pytest_configure + 13 more (~2927 tok)
- `to_interpreter.py` — _Worker: destroy, call, destroy, call + 4 more (~2029 tok)
- `to_process.py` — from: run_sync, send_raw_command, current_default_process_limiter, process_worker (~2800 tok)
- `to_thread.py` — run_sync, current_default_thread_limiter (~768 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/anyio/_backends/

- `__init__.py` (~0 tok)
- `_asyncio.py` — _State: close, get_loop, run, find_root_task + 2 more (~28247 tok)
- `_trio.py` — from: cancel, deadline, deadline, cancel_called + 25 more (~11845 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/anyio/_core/

- `__init__.py` (~0 tok)
- `_asyncio_selector_thread.py` — Selector: start, add_reader, add_writer, remove_reader + 3 more (~1608 tok)
- `_contextmanagers.py` — Declares _SupportsCtxMgr (~2062 tok)
- `_eventloop.py` — because: run, sleep, sleep_forever, sleep_until + 9 more (~1842 tok)
- `_exceptions.py` — BrokenResourceError: iterate_exceptions (~1260 tok)
- `_fileio.py` — from: wrapped, aclose, read, read1 + 36 more (~7352 tok)
- `_resources.py` — aclose_forcefully (~125 tok)
- `_signals.py` — open_signal_receiver (~291 tok)
- `_sockets.py` — URL configuration (~9992 tok)
- `_streams.py` — Declares create_memory_object_stream (~516 tok)
- `_subprocesses.py` — run_process, drain_stream, open_process (~2300 tok)
- `_synchronization.py` — from: set, is_set, wait, statistics + 29 more (~5963 tok)
- `_tasks.py` — _IgnoredTaskStatus: started, cancel, deadline, deadline + 8 more (~1553 tok)
- `_tempfile.py` — TemporaryFile: aclose, rollover, closed, read + 6 more (~5628 tok)
- `_testing.py` — TaskInfo: has_pending_cancellation, get_current_task, get_running_tasks, wait_all_tasks_blocked (~669 tok)
- `_typedattr.py` — TypedAttributeSet: typed_attribute, extra_attributes, extra, extra + 1 more (~717 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/anyio/abc/

- `__init__.py` (~820 tok)
- `_eventloop.py` — AsyncBackend: run, current_token, current_time, cancelled_exception_class + 43 more (~3072 tok)
- `_resources.py` — AsyncResource: aclose (~224 tok)
- `_sockets.py` — SocketAttribute: extra_attributes, from_socket, from_socket, send_fds + 9 more (~3788 tok)
- `_streams.py` — UnreliableObjectReceiveStream: receive, send, send_eof, receive + 5 more (~2183 tok)
- `_subprocesses.py` — Process: wait, terminate, kill, send_signal + 5 more (~591 tok)
- `_tasks.py` — TaskStatus: started, started, started, start_soon + 1 more (~1064 tok)
- `_testing.py` — TestRunner: run_asyncgen_fixture, run_fixture, run_test (~521 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/anyio/streams/

- `__init__.py` (~0 tok)
- `buffered.py` — BufferedByteReceiveStream: aclose, buffer, extra_attributes, feed_data + 6 more (~1790 tok)
- `file.py` — URL configuration (~1278 tok)
- `memory.py` — MemoryObjectStreamStatistics: statistics, receive_nowait, receive, clone + 9 more (~3069 tok)
- `stapled.py` — from: receive, send, send_eof, aclose + 9 more (~1255 tok)
- `text.py` — TextReceiveStream: receive, aclose, extra_attributes, send + 8 more (~1648 tok)
- `tls.py` — from: wrap, unwrap, aclose, receive + 4 more (~4391 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/certifi-2026.2.25.dist-info/

- `INSTALLER` (~2 tok)
- `METADATA` (~660 tok)
- `RECORD` (~273 tok)
- `top_level.txt` (~2 tok)
- `WHEEL` (~25 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/certifi-2026.2.25.dist-info/licenses/

- `LICENSE` — Project license (~264 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/certifi/

- `__init__.py` (~27 tok)
- `__main__.py` (~70 tok)
- `cacert.pem` — Issuer: CN=QuoVadis Root CA 2 O=QuoVadis Limited (~72651 tok)
- `core.py` — URL patterns: 3 routes (~970 tok)
- `py.typed` (~0 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/click-8.3.1.dist-info/

- `INSTALLER` (~2 tok)
- `METADATA` — Declares toolkit (~699 tok)
- `RECORD` (~675 tok)
- `WHEEL` (~22 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/click-8.3.1.dist-info/licenses/

- `LICENSE.txt` (~369 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/click/

- `__init__.py` (~1278 tok)
- `_compat.py` — URL configuration (~5341 tok)
- `_termui_impl.py` — ProgressBar: render_finish, pct, time_per_iteration, eta + 11 more (~7741 tok)
- `_textwrap.py` — TextWrapper: extra_indent, indent_only (~400 tok)
- `_utils.py` — Declares import (~270 tok)
- `_winconsole.py` — This module is based on the excellent work by Adam Bartoš who (~2419 tok)
- `core.py` — ParameterSource: batch, augment_usage_errors, iter_params_for_processing, sort_key (~37745 tok)
- `decorators.py` — to: pass_context, new_func, pass_obj, new_func + 24 more (~5275 tok)
- `exceptions.py` — ClickException: format_message, show, show, format_message + 4 more (~2844 tok)
- `formatting.py` — Can force a width.  This is used by the test system (~2780 tok)
- `globals.py` — get_current_context, get_current_context, get_current_context, push_context + 2 more (~550 tok)
- `parser.py` — _Option: takes_value, process, process, add_option + 2 more (~5432 tok)
- `py.typed` (~0 tok)
- `shell_completion.py` — CompletionItem: shell_complete, func_name, source_vars, source + 9 more (~5999 tok)
- `termui.py` — hidden_prompt_func, prompt, prompt_func, confirm + 4 more (~8868 tok)
- `testing.py` — EchoingStdin: read, read1, readline, readlines + 14 more (~5458 tok)
- `types.py` — ParamType: to_info_dict, get_metavar, get_missing_message, convert + 14 more (~11408 tok)
- `utils.py` — URL configuration (~5788 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/filelock-3.25.2.dist-info/

- `INSTALLER` (~2 tok)
- `METADATA` (~527 tok)
- `RECORD` (~498 tok)
- `WHEEL` (~24 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/filelock-3.25.2.dist-info/licenses/

- `LICENSE` — Project license (~290 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/filelock/

- `__init__.py` (~659 tok)
- `_api.py` — URL configuration (~6045 tok)
- `_async_read_write.py` — Async wrapper around :class:`ReadWriteLock` for use with ``asyncio``. (~2156 tok)
- `_error.py` — Timeout: lock_file (~226 tok)
- `_read_write.py` — URL configuration (~4378 tok)
- `_soft.py` — Declares SoftFileLock (~1337 tok)
- `_unix.py` — : a flag to indicate if the fcntl API is available (~1308 tok)
- `_util.py` — raise_on_not_writable_file, ensure_directory_exists (~491 tok)
- `_windows.py` — Declares WindowsFileLock (~1127 tok)
- `asyncio.py` — An asyncio-based implementation of the file lock. (~3984 tok)
- `py.typed` (~0 tok)
- `version.py` — file generated by setuptools-scm (~202 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/fsspec-2026.2.0.dist-info/

- `INSTALLER` (~2 tok)
- `METADATA` (~2807 tok)
- `RECORD` (~2222 tok)
- `WHEEL` (~24 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/fsspec-2026.2.0.dist-info/licenses/

- `LICENSE` — Project license (~404 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/fsspec/

- `__init__.py` — process_entries (~587 tok)
- `_version.py` — file generated by setuptools-scm (~203 tok)
- `archive.py` — AbstractArchiveFileSystem: ukey, info, ls (~689 tok)
- `asyn.py` — URL configuration (~10466 tok)
- `caching.py` — BaseCache: block (~9721 tok)
- `callbacks.py` — Callback: close, branched, branch_coro, func + 14 more (~2632 tok)
- `compression.py` — Helper functions for a standard streaming compression API (~1467 tok)
- `config.py` — Augments: set_conf_env, set_conf_files, apply_config (~1223 tok)
- `conftest.py` — InstanceCacheInspector: m, clear, gather_counts, instance_caches + 2 more (~985 tok)
- `core.py` — for backwards compat, we export cache things from here too (~6907 tok)
- `dircache.py` — DirCache: clear (~777 tok)
- `exceptions.py` — Declares BlocksizeMismatchError (~95 tok)
- `fuse.py` — FUSEr: getattr, readdir, mkdir, rmdir + 11 more (~2908 tok)
- `generic.py` — GenericFileSystem: set_generic_fs, rsync, rsync, copy_file_op (~3852 tok)
- `gui.py` — URL configuration (~3998 tok)
- `json.py` — from: default, make_serializable, try_resolve_path_cls, try_resolve_fs_cls + 2 more (~1077 tok)
- `mapping.py` — FSMap: dirfs, clear, getitems, setitems + 4 more (~2384 tok)
- `parquet.py` — Parquet-Specific Utilities for fsspec (~5859 tok)
- `registry.py` — to: register_implementation, get_filesystem_class, filesystem, available_protocols (~3471 tok)
- `spec.py` — URL configuration (~22194 tok)
- `transaction.py` — Transaction: start, complete, commit, discard + 2 more (~686 tok)
- `utils.py` — URL configuration (~6750 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/fsspec/implementations/

- `__init__.py` (~0 tok)
- `arrow.py` — ArrowFSWrapper: wrap_exceptions, wrapper, protocol, fsid + 14 more (~2540 tok)
- `asyn_wrapper.py` — AsyncFileSystemWrapper: async_wrapper, wrapper, fsid, wrap_class (~1068 tok)
- `cache_mapper.py` — AbstractCacheMapper: create_cache_mapper (~692 tok)
- `cache_metadata.py` — CacheMetadata: check_file, clear_expired, load, on_close_cached_file + 3 more (~2430 tok)
- `cached.py` — WriteCachedTransaction: complete, cache_size, load_cache, save_cache + 3 more (~10336 tok)
- `chained.py` — Declares ChainedFileSystem (~195 tok)
- `dask.py` — DaskWorkerFileSystem: mkdir, rm, copy, mv + 2 more (~1276 tok)
- `data.py` — DataFileSystem: cat_file, info, encode (~465 tok)
- `dbfs.py` — DatabricksException: ls, makedirs, mkdir, rm + 1 more (~4634 tok)
- `dirfs.py` — View: put, get (~3476 tok)
- `ftp.py` — URL configuration (~3782 tok)
- `gist.py` — URL configuration (~2437 tok)
- `git.py` — GitFileSystem: ls, info, ukey (~1066 tok)
- `github.py` — URL configuration (~3330 tok)
- `http_sync.py` — This file is largely copied from http.py (~8667 tok)
- `http.py` — HTTPFileSystem: get_client, fsid, encode_url, close_session + 2 more (~8765 tok)
- `jupyter.py` — JupyterFileSystem: ls, cat_file, pipe_file, mkdir + 1 more (~1144 tok)
- `libarchive.py` — LibArchiveFileSystem: custom_reader, read_func, seek_func (~2028 tok)
- `local.py` — URL configuration (~4866 tok)
- `memory.py` — URL configuration (~3002 tok)
- `reference.py` — ReferenceNotReachable: ravel_multi_index, np, pd, setup + 4 more (~13947 tok)
- `sftp.py` — SFTPFileSystem: mkdir, makedirs, rmdir, info + 6 more (~1693 tok)
- `smb.py` — URL configuration (~4354 tok)
- `tar.py` — Declares TarFileSystem (~1175 tok)
- `webhdfs.py` — https://hadoop.apache.org/docs/r1.0.4/webhdfs.html (~5026 tok)
- `zip.py` — ZipFileSystem: close, pipe_file, find, to_parts (~1758 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/fsspec/tests/abstract/

- `__init__.py` — URL configuration (~2852 tok)
- `common.py` (~1421 tok)
- `copy.py` — AbstractCopyTests: test_copy_file_to_existing_directory, test_copy_file_to_new_directory, test_copy_file_to_file_in_existing_directory, test_copy_f... (~5705 tok)
- `get.py` — AbstractGetTests: test_get_file_to_existing_directory, test_get_file_to_new_directory, test_get_file_to_file_in_existing_directory, test_get_file_t... (~5930 tok)
- `mv.py` — test_move_raises_error_with_tmpdir, test_move_raises_error_with_tmpdir_permission (~567 tok)
- `open.py` — AbstractOpenTests: test_open_exclusive (~94 tok)
- `pipe.py` — AbstractPipeTests: test_pipe_exclusive (~115 tok)
- `put.py` — AbstractPutTests: test_put_file_to_existing_directory, test_put_file_to_new_directory, test_put_file_to_file_in_existing_directory, test_put_file_t... (~6058 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/h11-0.16.0.dist-info/

- `INSTALLER` (~2 tok)
- `METADATA` (~2227 tok)
- `RECORD` (~488 tok)
- `top_level.txt` (~1 tok)
- `WHEEL` (~25 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/h11-0.16.0.dist-info/licenses/

- `LICENSE.txt` (~281 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/h11/

- `__init__.py` — A highish-level implementation of the HTTP/1.1 wire protocol (RFC 7230), (~431 tok)
- `_abnf.py` — We use native strings for all the re patterns, to take advantage of string (~1376 tok)
- `_connection.py` — This contains the main Connection class. Everything in h11 revolves around (~7676 tok)
- `_events.py` — High level events that make up HTTP/1.1 conversations. Loosely inspired by (~3370 tok)
- `_headers.py` — Headers: raw_items, normalize_and_validate, normalize_and_validate, normalize_and_validate + 4 more (~2975 tok)
- `_readers.py` — Code to read HTTP data (~2455 tok)
- `_receivebuffer.py` — ReceiveBuffer: maybe_extract_at_most, maybe_extract_next_line, maybe_extract_lines, is_next_line_obviously_invalid_request_line (~1501 tok)
- `_state.py` — ############################################################### (~3781 tok)
- `_util.py` — ProtocolError: validate, bytesify (~1397 tok)
- `_version.py` — This file must be kept very simple, because it is consumed from several (~196 tok)
- `_writers.py` — Code to read HTTP data (~1452 tok)
- `py.typed` (~2 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/hf_xet-1.4.2.dist-info/

- `INSTALLER` (~2 tok)
- `METADATA` (~1296 tok)
- `RECORD` (~189 tok)
- `WHEEL` (~39 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/hf_xet-1.4.2.dist-info/licenses/

- `LICENSE` — Project license (~3029 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/hf_xet-1.4.2.dist-info/sboms/

- `hf_xet.cyclonedx.json` — Declares built (~113363 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/hf_xet/

- `__init__.py` (~31 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/httpcore-1.0.9.dist-info/

- `INSTALLER` (~2 tok)
- `METADATA` — Declares html (~5741 tok)
- `RECORD` (~1270 tok)
- `WHEEL` (~24 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/httpcore-1.0.9.dist-info/licenses/

- `LICENSE.md` (~380 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/httpcore/

- `__init__.py` — Declares is (~985 tok)
- `_api.py` — request, stream (~899 tok)
- `_exceptions.py` — ConnectionNotAvailable: map_exceptions (~339 tok)
- `_models.py` — Functions for typechecking... (~5036 tok)
- `_ssl.py` — default_ssl_context (~54 tok)
- `_synchronization.py` — Our async synchronization primatives use either 'anyio' or 'trio' depending (~2696 tok)
- `_trace.py` — Trace: trace, atrace (~1130 tok)
- `_utils.py` — is_socket_readable (~440 tok)
- `py.typed` (~0 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/httpcore/_async/

- `__init__.py` — Declares AsyncHTTP2Connection (~349 tok)
- `connection_pool.py` — AsyncPoolRequest: assign_to_connection, clear_connection, wait_for_connection, is_queued + 3 more (~4945 tok)
- `connection.py` — AsyncHTTPConnection: exponential_backoff, handle_async_request, can_handle_request, aclose + 5 more (~2414 tok)
- `http_proxy.py` — AsyncHTTPProxy: merge_headers, create_connection, handle_async_request, can_handle_request + 7 more (~4201 tok)
- `http11.py` — HTTPConnectionState: handle_async_request, aclose, can_handle_request, is_available + 4 more (~3966 tok)
- `http2.py` — HTTPConnectionState: has_body_headers, handle_async_request (~6839 tok)
- `interfaces.py` — AsyncRequestInterface: request, stream, handle_async_request, aclose + 6 more (~1273 tok)
- `socks_proxy.py` — AsyncSOCKSProxy: create_connection, handle_async_request, can_handle_request (~3955 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/httpcore/_backends/

- `__init__.py` (~0 tok)
- `anyio.py` — AnyIOStream: read, write, aclose, start_tls + 4 more (~1501 tok)
- `auto.py` — AutoBackend: connect_tcp, connect_unix_socket, sleep (~475 tok)
- `base.py` — NetworkStream: read, write, close, start_tls + 12 more (~870 tok)
- `mock.py` — MockSSLObject: selected_alpn_protocol, read, write, close + 13 more (~1165 tok)
- `sync.py` — TLSinTLSStream: read, write, close, start_tls + 8 more (~2280 tok)
- `trio.py` — TrioStream: read, write, aclose, start_tls + 4 more (~1714 tok)

## backend/src/main/resources/models/.venv/lib/python3.13/site-packages/httpcore/_sync/

- `__init__.py` — Declares HTTP2Connection (~326 tok)
- `connection_pool.py` — PoolRequest: assign_to_connection, clear_connection, wait_for_connection, is_queued + 3 more (~4845 tok)
- `connection.py` — HTTPConnection: exponential_backoff, handle_request, can_handle_request, close + 5 more (~2354 tok)
- `http_proxy.py` — HTTPProxy: merge_headers, create_connection, handle_request, can_handle_request + 7 more (~4133 tok)
- `http11.py` — HTTPConnectionState: handle_request, close, can_handle_request, is_available + 5 more (~3851 tok)
- `http2.py` — HTTPConnectionState: has_body_headers, handle_request (~6686 tok)
- `interfaces.py` — RequestInterface: request, stream, handle_request, close + 6 more (~1242 tok)
- `socks_proxy.py` — SOCKSProxy: create_connection, handle_request, can_handle_request, close + 1 more (~3890 tok)

## backend/src/test/java/at/querchecker/auth/

- `AccessKeyControllerTest.java` — Class: AccessKeyControllerTest (~1610 tok)
- `AccessKeyServiceTest.java` — Class: AccessKeyServiceTest (~1411 tok)
- `AccessKeyUsageServiceTest.java` — Class: AccessKeyUsageServiceTest (~586 tok)
- `AuthControllerTest.java` — Class: AuthControllerTest (~756 tok)
- `AuthServiceTest.java` — Class: AuthServiceTest (~2078 tok)
- `SessionCookieAuthFilterTest.java` — Class: SessionCookieAuthFilterTest (~1884 tok)

## backend/src/test/java/at/querchecker/research/

- `ProductLookupServiceTest.java` — Setzt eine USER-Session mit gegebenem accessKeyId in den SecurityContext. (~5331 tok)

## config/

- `querchecker.yml` (~884 tok)

## docs/

- `admin-guide.md` — Querchecker Admin Guide (~3031 tok)
- `architecture.md` — Architecture & Design Decisions (~2606 tok)
- `auth-guide.md` — Querchecker Auth Guide (~950 tok)
- `dev-setup.md` — Querchecker — Developer Setup (~1242 tok)
- `extraction-engine.md` — Extraction Engine — Architecture Reference (~1118 tok)
- `ki-product-analysis.md` — Automatische KI-Produktanalyse — Konzept & Architektur (~4405 tok)
- `ki-produktanalyse.md` — Automatische KI-Produktanalyse — Konzept & Architektur (~2857 tok)
- `local-models.md` — Kurzanleitung: Lokale KI-Modelle (~1689 tok)
- `open-issues.md` — Open Issues — Querchecker v0.2.0 (~1551 tok)
- `openrouter-completion.md` — OpenRouter — Completion Checklist (~1608 tok)
- `robustness.md` — Robustness & Error Handling (~2487 tok)

## docs/auth/

- `berechtigung-P4-kontingent-zaehlung.md` — Berechtigungskonzept — Implementierungs-Prompt P4 (Kontingent-Zählung) (~1243 tok)
- `berechtigungen-konzept.md` — Querchecker — Berechtigungs- & Kontingent-Konzept (~5591 tok)

## docs/concepts/

- `provider-config.md` — Querchecker — Konzept: Provider-Konfiguration & Graceful Startup (~9036 tok)

## frontend/

- `package.json` — Node.js package manifest (~425 tok)

## frontend/src/

- `styles.scss` — Styles: 17 rules, 40 vars (~1495 tok)

## frontend/src/app/

- `app.config.ts` — Exports appConfig (~536 tok)

## frontend/src/app/api/model/

- `lookupHistoryEntryDto.ts` — OpenAPI definition (~364 tok)
- `lookupResponse.ts` — OpenAPI definition (~413 tok)

## frontend/src/app/core/

- `access-key.service.ts` — Exports AccessKeyOverview, AccessKeyCreated, AccessKeyService (~433 tok)
- `api-urls.ts` — Exports API_URLS (~387 tok)
- `auth.service.ts` — False for the dev-only LocalProfileAuthFilter SUPERUSER — there's no session to log out of. (~688 tok)
- `dl-extraction.service.ts` — Best term from the configured source model — pre-fills the research search field. (~402 tok)
- `http-error.interceptor.ts` — Detects server errors and network failures, then notifies the HealthService (~663 tok)

## frontend/src/app/core/model/

- `lookup.model.ts` — Raw JSON string from backend — parsed into featureGroups by ExtractionStore. (~378 tok)

## frontend/src/app/core/provider-status-popup/

- `provider-status-popup.html` (~285 tok)
- `provider-status-popup.scss` — Styles: 7 rules (~348 tok)

## frontend/src/app/features/settings/

- `settings.component.ts` — Exports SettingsComponent (~411 tok)
- `settings.html` (~1538 tok)
- `settings.scss` — Styles: 15 rules (~625 tok)
- `settings.ts` — API routes: GET (1 endpoints) (~866 tok)

## frontend/src/app/features/settings/access-key-management/

- `access-key-management.html` (~1321 tok)
- `access-key-management.scss` — Styles: 6 rules (~634 tok)
- `access-key-management.ts` — Exports AccessKeyManagement (~1533 tok)

## frontend/src/app/features/settings/provider-config/

- `provider-config.html` (~740 tok)
- `provider-config.scss` — Styles: 11 rules, 1 vars (~626 tok)
- `provider-config.ts` — Exports ProviderConfig (~1006 tok)

## frontend/src/app/features/settings/usage-monitor/

- `usage-monitor.scss` — Styles: 5 rules (~620 tok)

## frontend/src/app/features/wh-search/

- `extraction.store.ts` — Maps listingId → whItemId so SSE lookup-result events can update the store. (~4692 tok)
- `search.store.ts` — Exports SearchStore (~2752 tok)

## frontend/src/app/features/wh-search/wh-detail/

- `wh-detail.html` (~175 tok)
- `wh-detail.scss` — Styles: 8 rules, 1 vars (~592 tok)
- `wh-detail.ts` — Exports WhDetailComponent (~1082 tok)

## frontend/src/app/features/wh-search/wh-detail/item-annotation/

- `item-annotation.component.scss` — Styles: 11 rules (~750 tok)

## frontend/src/app/features/wh-search/wh-detail/item-research/

- `item-research.component.scss` — Styles: 17 rules, 5 vars (~1666 tok)
- `item-research.html` (~2941 tok)
- `item-research.scss` — Styles: 17 rules, 5 vars (~1731 tok)
- `item-research.ts` — True when AI search is usable. (~5878 tok)

## frontend/src/app/features/wh-search/wh-detail/item-research/icecat-accordion/

- `icecat-accordion.component.scss` — Styles: 4 rules (~622 tok)

## frontend/src/app/features/wh-search/wh-detail/item-research/specs-accordion/

- `specs-accordion.component.scss` — Styles: 4 rules (~622 tok)

## frontend/src/app/shared/components/confirm-dialog/

- `confirm-dialog.html` (~121 tok)
- `confirm-dialog.scss` — Styles: 2 rules (~110 tok)
- `confirm-dialog.ts` — Renders the confirm button in the error color for destructive actions. (~282 tok)

## frontend/src/app/shared/components/image-lightbox/

- `image-lightbox.scss` — Styles: 10 rules (~701 tok)

## frontend/src/app/shared/components/placeholder/

- `placeholder.ts` — Exports PlaceholderComponent (~81 tok)

## frontend/src/app/shared/layout/app-header/

- `app-header.component.ts` — Exports AppHeaderComponent (~570 tok)
- `app-header.html` (~346 tok)
- `app-header.scss` — Styles: 8 rules (~876 tok)
- `app-header.ts` — Exports AppHeaderComponent (~872 tok)

## frontend/src/app/shared/services/

- `confirm-dialog.service.ts` — Emits true if confirmed, false if cancelled/dismissed. (~182 tok)
