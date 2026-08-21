package io.javalibs.logging.spring;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

/** Captures the events the access log filter emits, so tests can assert on them. */
final class AccessLogRecorder {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender;

    private AccessLogRecorder(Logger logger, ListAppender<ILoggingEvent> appender) {
        this.logger = logger;
        this.appender = appender;
    }

    static AccessLogRecorder attach() {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new AccessLogRecorder(logger, appender);
    }

    void detach() {
        logger.detachAppender(appender);
        appender.stop();
    }

    List<ILoggingEvent> events() {
        return appender.list;
    }

    ILoggingEvent single() {
        List<ILoggingEvent> events = events();
        if (events.size() != 1) {
            throw new AssertionError("expected exactly one access log event but got " + events.size());
        }
        return events.get(0);
    }

    Object keyValue(String key) {
        List<KeyValuePair> pairs = single().getKeyValuePairs();
        if (pairs == null) {
            return null;
        }
        return pairs.stream()
                .filter(pair -> key.equals(pair.key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }
}
