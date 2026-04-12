package at.querchecker.api.result;

/**
 * Ergebnis-Typ für externe API-Calls (LLM + Web Search).
 * Ersetzt Exception-basiertes Fehler-Handling für Rate Limits, Netzwerkfehler und Auth-Fehler.
 *
 * <ul>
 *   <li>{@link Success} — Call erfolgreich, Ergebnis vorhanden</li>
 *   <li>{@link RateLimited} — HTTP 429, Provider bittet um Wartezeit</li>
 *   <li>{@link Unreachable} — Temporärer Fehler (503, Timeout, Netzwerk)</li>
 *   <li>{@link Unavailable} — Permanenter Fehler (401/403) → YAML-Korrektur + Neustart nötig</li>
 * </ul>
 */
public sealed interface ApiCallResult<T>
    permits ApiCallResult.Success, ApiCallResult.RateLimited,
            ApiCallResult.Unreachable, ApiCallResult.Unavailable {

    record Success<T>(T value) implements ApiCallResult<T> {}

    record RateLimited<T>(int retryAfterSeconds) implements ApiCallResult<T> {}

    record Unreachable<T>(String reason, int httpStatus) implements ApiCallResult<T> {}

    record Unavailable<T>(String reason, int httpStatus) implements ApiCallResult<T> {}
}
