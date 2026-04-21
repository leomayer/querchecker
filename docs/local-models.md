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

| Modell-Klasse            | Modell                                  | VRAM    | Bemerkung                         |
| ------------------------ | --------------------------------------- | ------- | --------------------------------- |
| `Llama32ExtractionModel` | meta-llama/Llama-3.2-3B-Instruct (GGUF) | ~2 GB   | primäres lokales Modell, Standard |
| `MdebertaExtractionModel`| mDeBERTa-v3-base-squad2                 | ~700 MB | NER-basiert, kein Instruct        |
| `NuExtractExtractionModel`| numind/NuExtract-v1.5-tiny             | ~400 MB | Qwen2-0.5B-Finetune               |
| `NuExtract15ExtractionModel`| numind/NuExtract-v1.5                | ~1 GB   | Qwen2-1.5B-Finetune               |
| `Qwen25ExtractionModel`  | Qwen/Qwen2.5-3B-Instruct (GGUF)        | ~2 GB   | alternativ zu Llama 3.2           |

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

## Konfiguration in `application.yml`

```yaml
querchecker:
  llm:
    mode: LOCAL          # LOCAL | API
```

Beim Start mit `mode: LOCAL` werden **ausschließlich lokale Modelle** registriert —
keine API-Keys nötig, kein Cloud-Zugriff.

---

## Modell aktivieren / deaktivieren

Lokale Modelle werden über die Datenbank-Tabelle `dl_model_config` verwaltet.

Aktives Modell prüfen:

```sql
SELECT name, active, execution_order FROM dl_model_config ORDER BY execution_order;
```

Modell aktivieren / deaktivieren über die Settings-UI unter
**Einstellungen → Textanalyse-Engine**.

---

## Mögliche Fehlerursachen

Wenn ein lokales Modell nicht startet, zeigt Querchecker in
**Einstellungen → Provider-Status** die Fehlermeldung aus dem DJL-Stack-Trace.

Häufige Ursachen:

| Fehler                                      | Ursache                              | Lösung                                       |
| ------------------------------------------- | ------------------------------------ | -------------------------------------------- |
| `UnsatisfiedLinkError`                      | Native Library fehlt                 | `libstdc++6` installieren                    |
| `FileNotFoundException` (GGUF-Datei)        | Modell nicht heruntergeladen         | Download-Skript ausführen                    |
| `OutOfMemoryError`                          | Zu wenig RAM/VRAM                    | Kleineres Modell wählen                      |
| `ClassNotFoundException` (JNI)              | DevTools ClassLoader-Problem         | `spring-devtools.properties` prüfen          |
| Modell startet, liefert aber schlechte Ergebnisse | Falsches Modell für Kategorie  | Llama 3.2 oder Qwen 2.5 bevorzugen          |
