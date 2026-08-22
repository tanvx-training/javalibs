package io.javalibs.logging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code response} subtree of a log record.
 *
 * @param status       HTTP status code
 * @param latencyMs    time taken to serve the request, in milliseconds
 * @param responseBody response payload — a parsed {@code Map} for JSON, a
 *                     {@code String} otherwise, or {@code null} when not captured
 */
public record HttpResponseLog(int status, long latencyMs, Object responseBody) {

    /**
     * Renders this response as a map in schema field order, omitting the body
     * when it was not captured.
     *
     * @return an ordered map ready to be written as JSON
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>(3);
        map.put(LogFields.STATUS, status);
        map.put(LogFields.LATENCY_MS, latencyMs);
        if (responseBody != null) {
            map.put(LogFields.RESPONSE_BODY, responseBody);
        }
        return map;
    }
}
