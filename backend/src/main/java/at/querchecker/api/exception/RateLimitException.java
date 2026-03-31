package at.querchecker.api.exception;

import at.querchecker.api.entity.Provider;

/**
 * Thrown when an API provider returns HTTP 429 Too Many Requests.
 * Carries the retry delay parsed from the Retry-After response header.
 */
public class RateLimitException extends RuntimeException {

    private final int retryAfterSeconds;
    private final Provider provider;
    private final String modelName;

    public RateLimitException(int retryAfterSeconds, Provider provider, String modelName) {
        super("Rate limited by " + provider + " — retry in " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
        this.provider = provider;
        this.modelName = modelName;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    /** Parses the Retry-After header value (seconds as integer or float). Defaults to 5s on parse failure. */
    public static int parseRetryAfter(String header) {
        if (header == null || header.isBlank()) return 5;
        try {
            double seconds = Double.parseDouble(header.trim());
            return Math.max(1, (int) Math.ceil(seconds));
        } catch (NumberFormatException e) {
            return 5;
        }
    }
}
