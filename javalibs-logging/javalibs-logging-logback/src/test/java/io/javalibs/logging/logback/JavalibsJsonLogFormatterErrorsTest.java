package io.javalibs.logging.logback;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JavalibsJsonLogFormatterErrorsTest {

    private final JavalibsJsonLogFormatter formatter =
            new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> errorsOf(LoggingEvent event) {
        return (List<Map<String, Object>>) LogEvents.parse(formatter.format(event)).get(LogFields.ERRORS);
    }

    @Test
    void writesAnEmptyArrayWhenThereIsNoThrowable() {
        Map<String, Object> record =
                LogEvents.parse(formatter.format(LogEvents.event(Level.INFO, "all good")));

        assertThat(record).containsKey(LogFields.ERRORS);
        assertThat((List<?>) record.get(LogFields.ERRORS)).isEmpty();
    }

    @Test
    void writesTypeMessageAndStacktraceForAThrowable() {
        LoggingEvent event = LogEvents.event(Level.ERROR, "Login failed");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("account locked")));

        List<Map<String, Object>> errors = errorsOf(event);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .containsEntry(LogFields.ERROR_TYPE, "java.lang.IllegalStateException")
                .containsEntry(LogFields.ERROR_MESSAGE, "account locked");
        assertThat((String) errors.get(0).get(LogFields.ERROR_STACKTRACE))
                .contains("JavalibsJsonLogFormatterErrorsTest");
    }

    @Test
    void walksTheWholeCauseChain() {
        Throwable root = new IllegalArgumentException("bad id");
        Throwable wrapper = new IllegalStateException("cannot load user", root);
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setThrowableProxy(new ThrowableProxy(wrapper));

        List<Map<String, Object>> errors = errorsOf(event);

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).containsEntry(LogFields.ERROR_TYPE, "java.lang.IllegalStateException");
        assertThat(errors.get(1)).containsEntry(LogFields.ERROR_TYPE, "java.lang.IllegalArgumentException");
    }

    @Test
    void omitsStacktraceWhenItIsSwitchedOff() {
        JavalibsJsonLogFormatter withoutTraces = new JavalibsJsonLogFormatter(
                LogEvents.settings(Map.of("javalibs.logging.stacktrace.enabled", "false")));
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("nope")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) LogEvents
                .parse(withoutTraces.format(event)).get(LogFields.ERRORS);

        assertThat(errors.get(0)).doesNotContainKey(LogFields.ERROR_STACKTRACE);
    }

    @Test
    void truncatesAStacktraceToTheConfiguredLength() {
        JavalibsJsonLogFormatter shortTraces = new JavalibsJsonLogFormatter(
                LogEvents.settings(Map.of("javalibs.logging.stacktrace.max-length", "40")));
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("nope")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) LogEvents
                .parse(shortTraces.format(event)).get(LogFields.ERRORS);

        assertThat((String) errors.get(0).get(LogFields.ERROR_STACKTRACE)).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void negativeMaxLengthDoesNotThrowAndKeepsAStacktrace() {
        JavalibsJsonLogFormatter negativeMaxLength = new JavalibsJsonLogFormatter(
                LogEvents.settings(Map.of("javalibs.logging.stacktrace.max-length", "-1")));
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("nope")));

        assertThatCode(() -> negativeMaxLength.format(event)).doesNotThrowAnyException();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) LogEvents
                .parse(negativeMaxLength.format(event)).get(LogFields.ERRORS);

        assertThat((String) errors.get(0).get(LogFields.ERROR_STACKTRACE)).isNotEmpty();
    }

    @Test
    void capsTheCauseChainAtTenEntries() {
        Throwable current = new IllegalStateException("level-0");
        for (int level = 1; level < 15; level++) {
            current = new IllegalStateException("level-" + level, current);
        }
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setThrowableProxy(new ThrowableProxy(current));

        List<Map<String, Object>> errors = errorsOf(event);

        assertThat(errors).hasSize(10);
    }

    @Test
    void placesErrorsBetweenTagsAndMetadata() {
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setMDCPropertyMap(Map.of(LogFields.MDC_TAGS, "auth", "correlationId", "abc"));
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("nope")));

        List<String> keys = List.copyOf(LogEvents.parse(formatter.format(event)).keySet());

        assertThat(keys.indexOf(LogFields.ERRORS)).isGreaterThan(keys.indexOf(LogFields.TAGS));
        assertThat(keys.indexOf(LogFields.ERRORS)).isLessThan(keys.indexOf(LogFields.METADATA));
    }
}
