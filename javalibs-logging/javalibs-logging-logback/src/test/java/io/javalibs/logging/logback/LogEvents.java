package io.javalibs.logging.logback;

import java.time.Instant;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Test helpers for building log events and reading back formatter output. */
final class LogEvents {

    /** Fixed event time so timestamp assertions are deterministic. */
    static final Instant FIXED_INSTANT = Instant.parse("2026-08-13T10:25:30.123Z");

    /**
     * A logger context private to this fixture, with its own {@code MDCAdapter}.
     *
     * <p>Logback's {@code LoggingEvent#getMDCPropertyMap()} lazily reads the MDC
     * through the logger context's {@code MDCAdapter} whenever the event was not
     * given an explicit MDC map — so every event needs a non-null logger context
     * for the formatter to call it without a {@code NullPointerException}. Using
     * the JVM-wide context obtained through {@code LoggerFactory.getILoggerFactory()}
     * would share its {@code MDCAdapter} with {@code org.slf4j.MDC}, so any test
     * anywhere in the same JVM that calls {@code MDC.put(...)} without cleaning
     * up would leak into every event built here that does not set its own MDC
     * map. This context is isolated instead: nothing but this fixture can reach
     * its {@code MDCAdapter}.</p>
     */
    private static final LoggerContext ISOLATED_CONTEXT = createIsolatedContext();

    private LogEvents() {
    }

    private static LoggerContext createIsolatedContext() {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        return context;
    }

    static LoggingEvent event(Level level, String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("io.javalibs.demo.AuthService");
        event.setLevel(level);
        event.setMessage(message);
        event.setInstant(FIXED_INSTANT);
        event.setThreadName("main");
        event.setLoggerContext(ISOLATED_CONTEXT);
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
