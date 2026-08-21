package io.javalibs.logging.logback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsJsonLogFormatterMaskingTest {

    private static LoggingEvent loginEvent() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "user@example.com");
        body.put("password", "hunter2");
        body.put("pin", 1234);
        body.put("amount", 99);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(LogFields.METHOD, "POST");
        request.put(LogFields.ENDPOINT, "/api/login");
        request.put(LogFields.HEADERS, Map.of("Authorization", "Bearer abc.def"));
        request.put(LogFields.BODY, body);

        LoggingEvent event = LogEvents.event(Level.INFO, "POST /api/login 200 152ms");
        event.setKeyValuePairs(List.of(new KeyValuePair(LogFields.REQUEST, request)));
        return event;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requestBodyOf(String json) {
        Map<String, Object> request = (Map<String, Object>) LogEvents.parse(json).get(LogFields.REQUEST);
        return (Map<String, Object>) request.get(LogFields.BODY);
    }

    @Test
    void masksSecretsNestedInsideTheRequestBody() {
        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body).containsEntry("password", "********");
    }

    @Test
    void masksNumericSecretsToo() {
        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body).containsEntry("pin", "********");
    }

    @Test
    void leavesOrdinaryFieldsUntouched() {
        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body)
                .containsEntry("email", "user@example.com")
                .containsEntry("amount", 99);
    }

    @Test
    void masksCredentialCarryingHeaders() {
        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) LogEvents
                .parse(formatter.format(loginEvent())).get(LogFields.REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) request.get(LogFields.HEADERS);

        assertThat(headers).containsEntry("Authorization", "********");
    }

    @Test
    void honoursAConfiguredReplacementAndExtraKeys() {
        JavalibsJsonLogFormatter formatter = new JavalibsJsonLogFormatter(LogEvents.settings(Map.of(
                "javalibs.logging.masking.value", "[redacted]",
                "javalibs.logging.masking.keys", "email")));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body)
                .containsEntry("password", "[redacted]")
                .containsEntry("email", "[redacted]");
    }

    @Test
    void writesEverythingInClearWhenMaskingIsSwitchedOff() {
        JavalibsJsonLogFormatter formatter = new JavalibsJsonLogFormatter(
                LogEvents.settings(Map.of("javalibs.logging.masking.enabled", "false")));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body).containsEntry("password", "hunter2");
    }

    @Test
    void masksSecretsInsideAMapNestedInAList() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("token", "abc.def.ghi");
        item.put("scope", "read");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(LogFields.METHOD, "POST");
        request.put(LogFields.ENDPOINT, "/api/sessions");
        request.put("list", List.of(item));

        LoggingEvent event = LogEvents.event(Level.INFO, "POST /api/sessions 200 42ms");
        event.setKeyValuePairs(List.of(new KeyValuePair(LogFields.REQUEST, request)));

        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        @SuppressWarnings("unchecked")
        Map<String, Object> parsedRequest =
                (Map<String, Object>) LogEvents.parse(formatter.format(event)).get(LogFields.REQUEST);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) parsedRequest.get("list");

        assertThat(list.get(0))
                .containsEntry("token", "********")
                .containsEntry("scope", "read");
    }

    @Test
    void masksAWholeListSittingAtASensitiveKey() {
        // "token" (singular) is one of SensitiveKeys.DEFAULT_KEYS; matching is by
        // exact normalized name, not substring, so the key must be spelled exactly
        // that way for this to exercise the real default policy.
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(LogFields.METHOD, "POST");
        request.put(LogFields.ENDPOINT, "/api/tokens");
        request.put("token", List.of("abc", "def"));

        LoggingEvent event = LogEvents.event(Level.INFO, "POST /api/tokens 200 12ms");
        event.setKeyValuePairs(List.of(new KeyValuePair(LogFields.REQUEST, request)));

        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        @SuppressWarnings("unchecked")
        Map<String, Object> parsedRequest =
                (Map<String, Object>) LogEvents.parse(formatter.format(event)).get(LogFields.REQUEST);

        assertThat(parsedRequest).containsEntry("token", "********");
    }

    @Test
    void leavesANullValueAtASensitivePathAsNull() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("password", null);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(LogFields.METHOD, "POST");
        request.put(LogFields.ENDPOINT, "/api/login");
        request.put(LogFields.BODY, body);

        LoggingEvent event = LogEvents.event(Level.INFO, "POST /api/login 200 5ms");
        event.setKeyValuePairs(List.of(new KeyValuePair(LogFields.REQUEST, request)));

        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        Map<String, Object> parsedBody = requestBodyOf(formatter.format(event));

        assertThat(parsedBody).containsEntry("password", null);
    }

    /**
     * A record (or any other POJO) handed to {@code addKeyValue(...)} is a known,
     * documented limitation of masking — see javalibs-logging/README.md § "Che dữ
     * liệu nhạy cảm" and docs/modules/logging.md § "Lưu ý & bẫy thường gặp" #1.
     *
     * <p>{@code Members.applyingValueProcessor(...)} only recurses into
     * {@code Map}/{@code List}/arrays when walking the object graph; an arbitrary
     * object is rendered through its own {@code toString()} before masking ever
     * sees a value to check, so a secret carried by a custom type reaches the log
     * untouched. This test pins that behavior deliberately: if a future Spring
     * Boot upgrade changes how {@code JsonWriter} renders unknown object types,
     * this test starts failing and flags that the documented warning needs a
     * second look — instead of the documentation silently going stale.</p>
     */
    @Test
    void documentsThatCustomObjectsAreNotMaskedBecauseTheyAreRenderedWithToString() {
        record Credentials(String email, String password) {
        }

        LoggingEvent event = LogEvents.event(Level.INFO, "Login attempt");
        event.setKeyValuePairs(
                List.of(new KeyValuePair("user", new Credentials("user@example.com", "hunter2"))));

        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        String json = formatter.format(event);

        // The secret is not masked...
        assertThat(json).contains("hunter2");
        // ...because it never became a structured value masking could walk: the
        // whole record was flattened to one opaque string via toString(), not to
        // a nested "email"/"password" object.
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) LogEvents.parse(json).get(LogFields.METADATA);
        assertThat(metadata.get("user")).isInstanceOf(String.class).asString().contains("hunter2");
    }
}
