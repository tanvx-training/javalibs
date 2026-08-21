package io.javalibs.logging.logback;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.javalibs.logging.LogFields;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.JsonWriterStructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * Renders every Logback event as one JSON record following the javalibs log
 * schema.
 *
 * <p>Activate it by pointing Spring Boot's structured logging at this class:</p>
 *
 * <pre>
 * logging.structured.format.console=io.javalibs.logging.logback.JavalibsJsonLogFormatter
 * </pre>
 *
 * <p>Setting {@code javalibs.logging.json.enabled=true} does the same thing
 * without naming the class — see
 * {@code JavalibsLoggingEnvironmentPostProcessor}.</p>
 *
 * <p>This class is instantiated by Spring Boot before the application context
 * exists, so it accepts only an {@link Environment} and never a bean.</p>
 */
public class JavalibsJsonLogFormatter extends JsonWriterStructuredLogFormatter<ILoggingEvent> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Constructor used by Spring Boot's structured logging support.
     *
     * @param environment the application environment
     */
    public JavalibsJsonLogFormatter(Environment environment) {
        this(JavalibsLogFormatSettings.from(environment));
    }

    JavalibsJsonLogFormatter(JavalibsLogFormatSettings settings) {
        super(JsonWriter.<ILoggingEvent>of(members -> configure(members, settings)).withNewLineAtEnd());
    }

    private static void configure(JsonWriter.Members<ILoggingEvent> members,
            JavalibsLogFormatSettings settings) {
        members.add(LogFields.TIMESTAMP, event -> TIMESTAMP_FORMAT.format(event.getInstant()));
        members.add(LogFields.LEVEL, event -> String.valueOf(event.getLevel()));
        members.add(LogFields.MESSAGE, ILoggingEvent::getFormattedMessage);
        members.add(LogFields.LOG_ID, event -> UUID.randomUUID().toString());
        members.add(LogFields.SERVICE, event -> settings.service()).whenHasLength();
        members.add(LogFields.HOST, event -> settings.host()).whenHasLength();
    }
}
