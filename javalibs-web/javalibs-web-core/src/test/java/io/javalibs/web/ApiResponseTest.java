package io.javalibs.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiResponse} static factories.
 */
class ApiResponseTest {

    @Test
    void okWrapsDataWithSuccessTrue() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.message()).isNull();
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.traceId()).isNull();
    }

    @Test
    void okWithMessageCarriesMessage() {
        ApiResponse<Integer> response = ApiResponse.ok(42, "created");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(42);
        assertThat(response.message()).isEqualTo("created");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void errorHasNoDataAndSuccessFalse() {
        ApiResponse<Object> response = ApiResponse.error("boom");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo("boom");
        assertThat(response.traceId()).isNull();
    }

    @Test
    void errorWithTraceIdCarriesTraceId() {
        ApiResponse<Object> response = ApiResponse.error("boom", "trace-123");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("boom");
        assertThat(response.traceId()).isEqualTo("trace-123");
    }
}
