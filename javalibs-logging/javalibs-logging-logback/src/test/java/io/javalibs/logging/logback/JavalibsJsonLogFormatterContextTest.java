package io.javalibs.logging.logback;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsJsonLogFormatterContextTest {

    private final JavalibsJsonLogFormatter formatter = new JavalibsJsonLogFormatter(
            LogEvents.settings(Map.of(
                    "javalibs.logging.service", "auth-service",
                    "javalibs.logging.tags", "auth",
                    "javalibs.logging.environment", "production",
                    "javalibs.logging.version", "1.2.3")));

    @Test
    void promotesUserIdAndIpFromMdcToTopLevelFields() {
        LoggingEvent event = LogEvents.event(Level.INFO, "User login successful");
        event.setMDCPropertyMap(Map.of(
                LogFields.MDC_USER_ID, "user_12345",
                LogFields.MDC_CLIENT_IP, "192.168.1.10"));

        Map<String, Object> record = LogEvents.parse(formatter.format(event));

        assertThat(record.get(LogFields.USER_ID)).isEqualTo("user_12345");
        assertThat(record.get(LogFields.IP)).isEqualTo("192.168.1.10");
    }

    @Test
    void doesNotRepeatPromotedMdcKeysInsideMetadata() {
        LoggingEvent event = LogEvents.event(Level.INFO, "hi");
        event.setMDCPropertyMap(Map.of(
                LogFields.MDC_USER_ID, "user_12345",
                LogFields.MDC_CLIENT_IP, "192.168.1.10",
                "correlationId", "8f14e45fceea167a"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata =
                (Map<String, Object>) LogEvents.parse(formatter.format(event)).get(LogFields.METADATA);

        assertThat(metadata)
                .containsEntry("correlationId", "8f14e45fceea167a")
                .containsEntry("env", "production")
                .containsEntry("version", "1.2.3")
                .doesNotContainKeys(LogFields.MDC_USER_ID, LogFields.MDC_CLIENT_IP);
    }

    @Test
    void writesRequestAndResponseSubtreesFromKeyValuePairs() {
        LoggingEvent event = LogEvents.event(Level.INFO, "POST /api/login 200 152ms");
        event.setKeyValuePairs(List.of(
                new KeyValuePair(LogFields.REQUEST,
                        Map.of(LogFields.METHOD, "POST", LogFields.ENDPOINT, "/api/login")),
                new KeyValuePair(LogFields.RESPONSE,
                        Map.of(LogFields.STATUS, 200, LogFields.LATENCY_MS, 152L))));

        Map<String, Object> record = LogEvents.parse(formatter.format(event));

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) record.get(LogFields.REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) record.get(LogFields.RESPONSE);
        assertThat(request).containsEntry(LogFields.ENDPOINT, "/api/login");
        assertThat(response).containsEntry(LogFields.STATUS, 200);
    }

    @Test
    void mergesStaticTagsWithRequestScopedAndPerLineTags() {
        LoggingEvent event = LogEvents.event(Level.INFO, "hi");
        event.setMDCPropertyMap(Map.of(LogFields.MDC_TAGS, "login, user"));
        event.setKeyValuePairs(List.of(new KeyValuePair(LogFields.TAGS, List.of("user", "audit"))));

        @SuppressWarnings("unchecked")
        List<String> tags =
                (List<String>) LogEvents.parse(formatter.format(event)).get(LogFields.TAGS);

        assertThat(tags).containsExactly("auth", "login", "user", "audit");
    }

    @Test
    void putsArbitraryKeyValuePairsIntoMetadata() {
        LoggingEvent event = LogEvents.event(Level.INFO, "Order created");
        event.setKeyValuePairs(List.of(new KeyValuePair("orderId", "ord-99")));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata =
                (Map<String, Object>) LogEvents.parse(formatter.format(event)).get(LogFields.METADATA);

        assertThat(metadata).containsEntry("orderId", "ord-99");
    }

    @Test
    void omitsUserIdIpRequestAndResponseForNonHttpEvents() {
        Map<String, Object> record =
                LogEvents.parse(formatter.format(LogEvents.event(Level.INFO, "Scheduled sweep done")));

        assertThat(record).doesNotContainKeys(
                LogFields.USER_ID, LogFields.IP, LogFields.REQUEST, LogFields.RESPONSE);
    }

    @Test
    void doesNotLeakRealMdcStateIntoEventsThatDoNotSetTheirOwnMdc() {
        MDC.put("leaked", "value");
        try {
            LoggingEvent event = LogEvents.event(Level.INFO, "hi");

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata =
                    (Map<String, Object>) LogEvents.parse(formatter.format(event)).get(LogFields.METADATA);

            assertThat(metadata).doesNotContainKey("leaked");
        } finally {
            MDC.clear();
        }
    }
}
