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
}
