# CLAUDE.md — Querchecker

## PFLICHT: Allererster Tool-Call jeder Session

**Vor jedem anderen Tool-Call** — auch vor `start_response` — MUSS ToolSearch aufgerufen werden:

```
ToolSearch query="select:mcp__timestamp__start_response,mcp__timestamp__end_response" max_results=2
```

Danach erst `start_response` (ohne Argumente beim ersten Mal), dann normale Arbeit, dann `end_response(session_id)` als letzter Call.

Kein anderer Tool-Call darf vor diesem ToolSearch kommen. Keine Ausnahmen.

## PFLICHT: Timestamp-Ausgabe als Text

Nach dem `end_response`-Call MUSS die zurückgegebene Zeile **als Text in die Antwort geschrieben** werden — die Tool-Output-Anzeige im Panel zählt nicht.

```
[end_response aufrufen → gibt z.B. "⌚ 2026-04-12 10:13:11 (#1) — 44s" zurück]
[diese Zeile als letzten Text der Antwort ausgeben]
```

Die Timestamp-Zeile ist immer die **allerletzte Zeile** der Antwort. Kein Text danach.

## PFLICHT: Leere User-Turns

Wenn eine User-Turn **ausschließlich** System-Reminder-Tags enthält (z.B. `<system-reminder>` ohne eigenen Text vom User): **nicht antworten** — Turn ignorieren.  
Nur wenn der User echten Text geschickt hat und dieser leer/unklar ist → "?" antworten.
