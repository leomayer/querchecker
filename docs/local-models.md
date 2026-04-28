# Kurzanleitung: Lokale KI-Modelle

Querchecker kann anstelle von Cloud-APIs (Groq, OpenRouter) auch lokal laufende
KI-Modelle für die Textanalyse verwenden. Die Modelle werden über
[DJL (Deep Java Library)](https://djl.ai/) und
[java-llama.cpp](https://github.com/kherud/java-llama.cpp) eingebunden.

---

## Voraussetzungen

### Native Libraries (Linux / openSUSE Tumbleweed)

java-llama.cpp verwendet native C++-Bibliotheken. Auf openSUSE Tumbleweed werden
diese automatisch über die JNI-Klassen heruntergeladen — keine manuelle Installation nötig.

Falls der Start fehlschlägt, prüfe:

```
libc.so.6 — glibc (normalerweise vorhanden)
libstdc++.so.6 — GNU C++ Standard Library
```

Installation (falls fehlend):

```bash
sudo zypper install libstdc++6
```

> **DevTools-Fix:** Der Spring DevTools ClassLoader verhindert das Laden des JNI-Jars.
> Die Datei `backend/src/main/resources/META-INF/spring-devtools.properties` enthält
> bereits den nötigen Fix — nichts weiter erforderlich.

---

## Verfügbare Modelle

| Modell-Klasse                | Modell                                  | VRAM    | Bemerkung                         |
| ---------------------------- | --------------------------------------- | ------- | --------------------------------- |
| `Llama32ExtractionModel`     | meta-llama/Llama-3.2-3B-Instruct (GGUF) | ~2 GB   | primäres lokales Modell, Standard |
| `MdebertaExtractionModel`    | mDeBERTa-v3-base-squad2                 | ~700 MB | NER-basiert, kein Instruct        |
| `NuExtractExtractionModel`   | numind/NuExtract-v1.5-tiny              | ~400 MB | Qwen2-0.5B-Finetune               |
| `NuExtract15ExtractionModel` | numind/NuExtract-v1.5                   | ~1 GB   | Qwen2-1.5B-Finetune               |
| `Qwen25ExtractionModel`      | Qwen/Qwen2.5-3B-Instruct (GGUF)         | ~2 GB   | alternativ zu Llama 3.2           |

---

## Modell herunterladen

Beim **ersten Start** lädt DJL das Modell automatisch von Hugging Face herunter.
Der lokale Cache liegt in:

```
~/.djl.ai/pytorch/
```

Alternativ können Modelle manuell mit den Python-Skripten heruntergeladen werden:

```bash
cd backend/src/main/resources/models
source .venv/bin/activate
python download_llama32.py
```

---

## Konfiguration in `config/querchecker.yml`

Der `mode`-Key ist bereits in `config/querchecker.yml` enthalten. Standard ist `API` — keine Änderung nötig für Cloud-Betrieb.

Für lokale Modelle auf `LOCAL` setzen:

```yaml
querchecker:
  llm:
    mode: LOCAL # API (Standard) | LOCAL
```

Beim Start mit `mode: LOCAL` werden **ausschließlich lokale Modelle** registriert —
keine API-Keys nötig, kein Cloud-Zugriff.

---

## Modell-Management via Datenbank

Lokale Modelle werden über die Datenbank-Tabelle `dl_model_config` verwaltet — nicht über die Kommandozeile.

### Tabelle: `dl_model_config`

| Spalte | Bedeutung |
|---|---|
| `name` | Modell-Name (z.B. `llama-3.2-3b`, `qwen3-4b`) |
| `active` | `true` = Modell ist verfügbar; `false` = ignoriert |
| `execution_order` | Priorität bei paralleler Verarbeitung (ascending) |
| `source_model` | `true` = dieses Modell liefert `suggestedTerm` (Suchfeld-Vorbefüllung) |

### Status prüfen

```sql
SELECT name, active, source_model, execution_order FROM dl_model_config ORDER BY execution_order;
```

### Modelle aktivieren / deaktivieren

**Über UI** (empfohlen):
- **Einstellungen → Textanalyse-Engine**
- Umschalten der `active`-Flag

**Per SQL** (manuell):

```sql
UPDATE dl_model_config SET active = true WHERE name = 'qwen3-4b';
UPDATE dl_model_config SET active = false WHERE name = 'llama-3.2-3b';
```

### Source-Modell wählen (suggestedTerm)

Das `source_model`-Flag bestimmt, welches Modell den `suggestedTerm` (Produktname zur Suche) liefert.
Nur ein Modell sollte `source_model = true` haben.

```sql
-- Qwen zum Source-Modell machen
UPDATE dl_model_config SET source_model = false WHERE source_model = true;
UPDATE dl_model_config SET source_model = true WHERE name = 'qwen3-4b';
```

---

## Mögliche Fehlerursachen

Wenn ein lokales Modell nicht startet, zeigt Querchecker in
**Einstellungen → Provider-Status** die Fehlermeldung aus dem DJL-Stack-Trace.

Häufige Ursachen:

| Fehler                                            | Ursache                       | Lösung                              |
| ------------------------------------------------- | ----------------------------- | ----------------------------------- |
| `UnsatisfiedLinkError`                            | Native Library fehlt          | `libstdc++6` installieren           |
| `FileNotFoundException` (GGUF-Datei)              | Modell nicht heruntergeladen  | Download-Skript ausführen           |
| `OutOfMemoryError`                                | Zu wenig RAM/VRAM             | Kleineres Modell wählen             |
| `ClassNotFoundException` (JNI)                    | DevTools ClassLoader-Problem  | `spring-devtools.properties` prüfen |
| Modell startet, liefert aber schlechte Ergebnisse | Falsches Modell für Kategorie | Llama 3.2 oder Qwen 2.5 bevorzugen  |

---

## Bekannte Einschränkungen

### Lokales LLM degradiert die Cache-Effizienz

Der `ProductLookup`-Cache ist ein **reiner String-Match** auf `lookupTerm`. Die Qualität des Caches steht und fällt damit, dass dasselbe Produkt immer denselben normalisierten Term erzeugt.

**API-Modelle (Groq / OpenRouter)** liefern konsistente Ergebnisse, weil:

- Sie mit 70B+ Parametern trainiert wurden und damit einen deutlich größeren Sprachmodellkontext mitbringen als lokale Quantisierungen (typisch 3B–8B, INT4/INT8)
- Sie gezielt durch RLHF/DPO auf Instruction-Following optimiert sind — strukturierte JSON-Ausgabe und exakte Produktnamen-Extraktion ist genau das, wofür diese Fine-Tuning-Stufen ausgelegt sind
- Produktnamen werden normalisiert: ein API-Modell gibt konsistent `"Sony WH-1000XM5"` zurück, unabhängig davon ob der Inseratstext `"Sony WH1000XM5 Kopfhörer schwarz wie neu"` oder `"WH-1000XM5 NC-Headset OVP"` enthält

**Lokale Modelle** dagegen können dasselbe Inserat unterschiedlich extrahieren:

- Run 1: `"Sony WH-1000XM5"`
- Run 2: `"Sony WH1000XM5 Schwarz"`
- Run 3: `"WH 1000XM5 Noise Cancelling"`

Jede Variante erzeugt einen eigenen `ProductLookup`-Eintrag, verbraucht einen Brave-API-Call, und kein Eintrag wird jemals wiederverwendet. Der Cache degeneriert zu einem reinen Write-Only-Log.

**Empfehlung:** Für produktiven Einsatz immer `querchecker.llm.active-provider: GROQ` oder `OPENROUTER` verwenden. Lokale Modelle eignen sich allenfalls für die Entwicklung ohne Internetabhängigkeit, aber nicht für zuverlässiges Caching.

---

## Siehe auch

- 💻 [Developer Setup](dev-setup.md) — Ersteinrichtung, secrets.yml, Troubleshooting
- ⚙️ [Admin Guide](admin-guide.md) — Provider-Konfiguration, Betrieb
