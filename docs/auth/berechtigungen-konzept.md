# Berechtigungskonzept — Offene Punkte

> Das Berechtigungskonzept ist vollständig umgesetzt. Der implementierte Stand (Rollen, Session-Mechanik, Kontingent-Ebenen, Design-Entscheidungen) ist im [Auth Guide](../auth-guide.md) dokumentiert — dieses Dokument enthält nur noch, was aussteht. (Konsolidiert 2026-07-09; Historie in Git.)

## Offen

- **Usage-Monitor: Breakdown pro Zugriffscode** (Ebene-2-Auswertung) — `access_key_usage` ist als History-Tabelle dafür ausgelegt (SQL-Aggregation nach Zugriffscode/Zeitraum), UI-Bereich noch nicht gebaut

## Zurückgestellt (bewusst, erst bei Bedarf)

- **Dynamische Drosselung bei hohem Traffic** — Key-Limits reduzieren bei z.B. ≥80% globalem Provider-Verbrauch; vorerst gilt: Provider erschöpft → alle sehen Fehlermeldung, auch Superuser
- **Nächtlicher Rollup-Job in Summary-Tabelle** — erst bei echtem Performance-Bedarf der Auswertung
