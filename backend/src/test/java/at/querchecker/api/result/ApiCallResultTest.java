package at.querchecker.api.result;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-Tests für ApiCallResult sealed interface.
 */
class ApiCallResultTest {

    // --- Success ---

    @Test
    void successHoldsValue() {
        ApiCallResult<String> result = new ApiCallResult.Success<>("hello");
        assertThat(result).isInstanceOf(ApiCallResult.Success.class);
        assertThat(((ApiCallResult.Success<String>) result).value()).isEqualTo("hello");
    }

    @Test
    void successWithNullValue() {
        ApiCallResult<String> result = new ApiCallResult.Success<>(null);
        assertThat(((ApiCallResult.Success<String>) result).value()).isNull();
    }

    @Test
    void successWithListValue() {
        List<String> list = List.of("a", "b");
        ApiCallResult<List<String>> result = new ApiCallResult.Success<>(list);
        assertThat(((ApiCallResult.Success<List<String>>) result).value()).hasSize(2);
    }

    // --- RateLimited ---

    @Test
    void rateLimitedHoldsRetryAfter() {
        ApiCallResult<String> result = new ApiCallResult.RateLimited<>(30);
        assertThat(result).isInstanceOf(ApiCallResult.RateLimited.class);
        assertThat(((ApiCallResult.RateLimited<String>) result).retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void rateLimitedZeroRetry() {
        ApiCallResult<String> result = new ApiCallResult.RateLimited<>(0);
        assertThat(((ApiCallResult.RateLimited<String>) result).retryAfterSeconds()).isZero();
    }

    // --- Unreachable ---

    @Test
    void unreachableHoldsReasonAndStatus() {
        ApiCallResult<String> result = new ApiCallResult.Unreachable<>("timeout", 503);
        assertThat(result).isInstanceOf(ApiCallResult.Unreachable.class);
        var u = (ApiCallResult.Unreachable<String>) result;
        assertThat(u.reason()).isEqualTo("timeout");
        assertThat(u.httpStatus()).isEqualTo(503);
    }

    // --- Unavailable ---

    @Test
    void unavailableHoldsReasonAndStatus() {
        ApiCallResult<String> result = new ApiCallResult.Unavailable<>("invalid api key", 401);
        assertThat(result).isInstanceOf(ApiCallResult.Unavailable.class);
        var u = (ApiCallResult.Unavailable<String>) result;
        assertThat(u.reason()).isEqualTo("invalid api key");
        assertThat(u.httpStatus()).isEqualTo(401);
    }

    // --- Pattern matching exhaustiveness ---

    @Test
    void switchCoversAllVariants() {
        List<ApiCallResult<String>> variants = List.of(
            new ApiCallResult.Success<>("ok"),
            new ApiCallResult.RateLimited<>(10),
            new ApiCallResult.Unreachable<>("net err", 503),
            new ApiCallResult.Unavailable<>("auth err", 401)
        );
        for (ApiCallResult<String> r : variants) {
            String label = switch (r) {
                case ApiCallResult.Success<String> s        -> "success";
                case ApiCallResult.RateLimited<String> rl   -> "rate-limited";
                case ApiCallResult.Unreachable<String> u    -> "unreachable";
                case ApiCallResult.Unavailable<String> u    -> "unavailable";
            };
            assertThat(label).isNotNull();
        }
    }

    @Test
    void successIsNotRateLimited() {
        ApiCallResult<String> result = new ApiCallResult.Success<>("ok");
        assertThat(result).isNotInstanceOf(ApiCallResult.RateLimited.class);
    }
}
