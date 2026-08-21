package io.javalibs.logging;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeLogTest {

    @Test
    void requestMapKeepsSchemaFieldOrder() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "user@example.com");
        HttpRequestLog request = new HttpRequestLog(
                "POST", "/api/login", Map.of("Content-Type", "application/json"), body);

        assertThat(request.toMap()).containsExactly(
                Map.entry(LogFields.METHOD, "POST"),
                Map.entry(LogFields.ENDPOINT, "/api/login"),
                Map.entry(LogFields.HEADERS, Map.of("Content-Type", "application/json")),
                Map.entry(LogFields.BODY, body));
    }

    @Test
    void requestMapOmitsEmptyHeadersAndAbsentBody() {
        HttpRequestLog request = new HttpRequestLog("GET", "/health", Map.of(), null);

        assertThat(request.toMap()).containsOnlyKeys(LogFields.METHOD, LogFields.ENDPOINT);
    }

    @Test
    void requestTreatsNullHeadersAsEmpty() {
        HttpRequestLog request = new HttpRequestLog("GET", "/health", null, null);

        assertThat(request.headers()).isEmpty();
    }

    @Test
    void responseMapKeepsSchemaFieldOrder() {
        Map<String, Object> body = Map.of("success", true);
        HttpResponseLog response = new HttpResponseLog(200, 152L, body);

        assertThat(response.toMap()).containsExactly(
                Map.entry(LogFields.STATUS, 200),
                Map.entry(LogFields.LATENCY_MS, 152L),
                Map.entry(LogFields.RESPONSE_BODY, body));
    }

    @Test
    void responseMapOmitsAbsentBody() {
        HttpResponseLog response = new HttpResponseLog(204, 3L, null);

        assertThat(response.toMap()).containsOnlyKeys(LogFields.STATUS, LogFields.LATENCY_MS);
    }
}
