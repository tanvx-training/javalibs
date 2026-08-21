package io.javalibs.logging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code request} subtree of a log record.
 *
 * @param method   HTTP method
 * @param endpoint request path, including the query string when present
 * @param headers  selected request headers, never {@code null} after construction
 * @param body     request payload — a parsed {@code Map} for JSON, a
 *                 {@code String} otherwise, or {@code null} when not captured
 */
public record HttpRequestLog(String method, String endpoint, Map<String, String> headers, Object body) {

    /** Canonical constructor normalizing {@code null} headers to an empty map. */
    public HttpRequestLog {
        headers = (headers == null) ? Map.of() : Map.copyOf(headers);
    }

    /**
     * Renders this request as a map in schema field order, omitting fields that
     * carry no value.
     *
     * @return an ordered map ready to be written as JSON
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>(4);
        map.put(LogFields.METHOD, method);
        map.put(LogFields.ENDPOINT, endpoint);
        if (!headers.isEmpty()) {
            map.put(LogFields.HEADERS, headers);
        }
        if (body != null) {
            map.put(LogFields.BODY, body);
        }
        return map;
    }
}
