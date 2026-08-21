package io.javalibs.logging.logback;

import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsJsonLogFormatterTest {

    private final JavalibsJsonLogFormatter formatter = new JavalibsJsonLogFormatter(
            LogEvents.settings(Map.of(
                    "javalibs.logging.service", "auth-service",
                    "javalibs.logging.host", "server-01")));

    @Test
    void writesTheEnvelopeFieldsInSchemaOrder() {
        LoggingEvent event = LogEvents.event(Level.INFO, "User login successful");

        Map<String, Object> record = LogEvents.parse(formatter.format(event));

        assertThat(record.keySet()).startsWith(
                LogFields.TIMESTAMP, LogFields.LEVEL, LogFields.MESSAGE, LogFields.LOG_ID,
                LogFields.SERVICE, LogFields.HOST);
        assertThat(record.get(LogFields.TIMESTAMP)).isEqualTo("2026-08-13T10:25:30.123Z");
        assertThat(record.get(LogFields.LEVEL)).isEqualTo("INFO");
        assertThat(record.get(LogFields.MESSAGE)).isEqualTo("User login successful");
        assertThat(record.get(LogFields.SERVICE)).isEqualTo("auth-service");
        assertThat(record.get(LogFields.HOST)).isEqualTo("server-01");
    }

    @Test
    void givesEveryEntryItsOwnLogId() {
        String first = (String) LogEvents.parse(
                formatter.format(LogEvents.event(Level.INFO, "one"))).get(LogFields.LOG_ID);
        String second = (String) LogEvents.parse(
                formatter.format(LogEvents.event(Level.INFO, "two"))).get(LogFields.LOG_ID);

        assertThat(first).matches("[0-9a-f-]{36}");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void endsEachRecordWithASingleNewlineSoOneLineIsOneEvent() {
        String rendered = formatter.format(LogEvents.event(Level.WARN, "careful"));

        assertThat(rendered).endsWith("\n");
        assertThat(rendered.stripTrailing()).doesNotContain("\n");
    }

    @Test
    void omitsServiceWhenItIsNotConfiguredButAlwaysWritesAHost() {
        JavalibsJsonLogFormatter bare = new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        Map<String, Object> record = LogEvents.parse(bare.format(LogEvents.event(Level.INFO, "hi")));

        assertThat(record).doesNotContainKey(LogFields.SERVICE);
        assertThat((String) record.get(LogFields.HOST)).isNotBlank();
    }

    @Test
    void survivesAnEventWithoutAMessage() {
        LoggingEvent event = LogEvents.event(Level.ERROR, null);

        Map<String, Object> record = LogEvents.parse(formatter.format(event));

        assertThat(record.get(LogFields.LEVEL)).isEqualTo("ERROR");
    }
}
