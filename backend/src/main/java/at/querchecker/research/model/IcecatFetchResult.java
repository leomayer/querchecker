package at.querchecker.research.model;

/**
 * Ergebnis eines Icecat-Spec-Abrufs — unterscheidet "nicht gefunden (404)" von anderen Fehlern.
 */
public record IcecatFetchResult(String json, boolean isNotFound) {

    public static IcecatFetchResult found(String json) {
        return new IcecatFetchResult(json, false);
    }

    public static IcecatFetchResult notFound() {
        return new IcecatFetchResult(null, true);
    }

    public static IcecatFetchResult error() {
        return new IcecatFetchResult(null, false);
    }
}
