package at.querchecker.config;

/**
 * Status eines externen Providers.
 *
 * <ul>
 *   <li>UNCONFIGURED — Key fehlt, leer, Whitespace oder == Platzhalter</li>
 *   <li>CONFIGURED   — Key syntaktisch vorhanden; inhaltliche Qualität unbekannt</li>
 *   <li>VALID        — Mindestens ein erfolgreicher API-Call seit dem Start</li>
 *   <li>UNREACHABLE  — Temporärer Fehler (503, Timeout, Netzwerk)</li>
 *   <li>UNAVAILABLE  — Permanenter Fehler (401/403) → YAML-Korrektur + Neustart nötig</li>
 * </ul>
 *
 * UNCONFIGURED und CONFIGURED entstehen nur beim Start (statische Prüfung).
 * VALID, UNREACHABLE, UNAVAILABLE entstehen erst nach einem echten API-Call.
 * Neustart setzt den Status immer auf UNCONFIGURED oder CONFIGURED zurück.
 */
public enum ProviderState {
    UNCONFIGURED,
    CONFIGURED,
    VALID,
    UNREACHABLE,
    UNAVAILABLE
}
