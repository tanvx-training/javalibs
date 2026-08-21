package io.javalibs.logging.logback;

import java.time.Instant;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Test helpers for building log events and reading back formatter output. */
final class LogEvents {

    /** Fixed event time so timestamp assertions are deterministic. */
    static final Instant FIXED_INSTANT = Instant.parse("2026-08-13T10:25:30.123Z");

    private LogEvents() {
    }

    static LoggingEvent event(Level level, String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("io.javalibs.demo.AuthService");
        event.setLevel(level);
        event.setMessage(message);
        event.setInstant(FIXED_INSTANT);
        event.setThreadName("main");
        // Logback's LoggingEvent#getMDCPropertyMap() lazily reads the MDC through
        // the logger context's MDCAdapter whenever the event was not given an
        // explicit MDC map. Wiring the real SLF4J logger context here mirrors
        // what Boot's logging pipeline always does, so the formatter can call
        // getMDCPropertyMap() on any event without a NullPointerException.
        event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());
        return event;
    }

    static JavalibsLogFormatSettings settings(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return JavalibsLogFormatSettings.from(environment);
    }

    static Map<String, Object> parse(String json) {
        return JsonParserFactory.getJsonParser().parseMap(json);
    }
}
