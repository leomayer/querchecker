# DL-Extraktion — Architektur-Referenz

Automatische Extraktion von Produktnamen/Modellbezeichnungen aus Willhaben-Inseraten für Kreuzsuchen (Geizhals, Brave etc.).

**Aktive Modelle:**

- `groq` — Cloud-API via Groq, `execution_order=5` (läuft zuerst, schnell)
- `llama` — Lokales GGUF-Modell via llama.cpp, `execution_order=10`
- `source-model: llama` in `application.yml` → nur llamas Term wird als `suggestedTerm` gesendet (füllt Suchfeld vor)

---

## Queue-Architektur: Designentscheidungen

Warum nicht parallel? — Lokale Modelle brauchen volle GPU, parallele Runs würden sich gegenseitig ausbremsen. Cloud-Modelle (Groq) unterliegen Rate Limits.

**Priorität:** Neueste Anfragen kommen zuerst dran (`addFirst()`). Sortierung nach `executionOrder` DESC → niedrigste executionOrder (= höchste Priorität) vorne. Nutzer sieht schnelleres Feedback für das Inserat, das er gerade offen hat.

**Cancellation bei Queue-Overflow:** Wenn das Queue-Limit (aus `AppConfig "dl.queue.limit"`, default 10) überschritten wird, fliegt das am Ende wartende Item (`pollLast()`) raus und bekommt Status `CANCELLED`. Das ist kein Fehler — `CANCELLED` Runs werden beim nächsten Öffnen des Inserats automatisch neu eingeplant.

**Warum CANCELLED nicht geskippt wird im Duplicate-Check:** Skip gilt nur für `DONE / INIT / PENDING`. CANCELLED runs werden gezielt nicht geskippt, damit der Retry-Mechanismus greift ohne extra Logik.

---

## Status-Maschine (`DlExtractionRun.status`)

```
INIT ──→ PENDING → DONE
                 ↘ FAILED
INIT → NO_IMPLEMENTATION  (terminal — kein ExtractionModel bean für diesen Modellnamen)
INIT → CANCELLED           (Queue-Overflow)
DONE/PENDING/INIT → RE_EVALUATE → INIT  (Term-Cleanup vorher)
```

`RE_EVALUATE` existiert für Fälle, wo ein früher extrahierter Term nachkorrigiert werden soll ohne den kompletten Run-History zu verlieren.

---

## Modell-Registrierung: Warum nicht `@Component`

Modelle sind **nicht** als Spring Beans registriert. `DlModelConfiguration` registriert sie erst beim `ApplicationReadyEvent`:

- **API-Mode** (`querchecker.llm.mode=API`): nur `LlmApiExtractionModel` — kein GGUF-Datei-Load
- **LOCAL-Mode**: nur die in der DB als `active=true` markierten Modelle

Vorteil: Der Server startet auch wenn keine GGUF-Dateien vorhanden sind. Lokale Modelle werden nur geladen wenn sie tatsächlich aktiv sind.

---

## Hinweis: Term-Qualität und Cache-Effizienz

Die Qualität des extrahierten Terms hat direkte Auswirkung auf den `ProductLookup`-Cache. Lokale Modelle können denselben Produktnamen unterschiedlich formulieren, was Cache-Treffer verhindert. Siehe → [Bekannte Einschränkungen in ki-product-analysis.md](ki-product-analysis.md#bekannte-einschränkungen).
