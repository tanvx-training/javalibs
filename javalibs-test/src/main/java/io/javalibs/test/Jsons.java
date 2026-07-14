package io.javalibs.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON helpers for tests: serialization round-trips and structural equality
 * assertions independent of key order and formatting.
 */
public final class Jsons {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Jsons() {
    }

    /** Shared, pre-configured mapper (Java time as ISO-8601 strings). */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new AssertionError("Failed to serialize " + value.getClass() + " to JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new AssertionError("Failed to deserialize JSON to " + type, e);
        }
    }

    /**
     * Asserts that two JSON documents are structurally equal (ignoring key order
     * and whitespace). Throws {@link AssertionError} with both documents in the
     * message when they differ.
     */
    public static void assertEquivalent(String expectedJson, String actualJson) {
        JsonNode expected = readTree(expectedJson, "expected");
        JsonNode actual = readTree(actualJson, "actual");
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "JSON documents differ.%nExpected: %s%nActual:   %s".formatted(expected, actual));
        }
    }

    private static JsonNode readTree(String json, String label) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("Invalid " + label + " JSON: " + json, e);
        }
    }
}
