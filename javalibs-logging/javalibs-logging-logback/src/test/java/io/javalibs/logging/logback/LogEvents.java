package io.javalibs.logging.logback;

import java.time.Instant;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
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
