package io.javalibs.logging.logback;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.javalibs.logging.LogFields;
import org.slf4j.event.KeyValuePair;
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

    /** Key-value keys rendered as dedicated fields, hence excluded from metadata. */
    private static final Set<String> RESERVED_KEY_VALUES =
            Set.of(LogFields.REQUEST, LogFields.RESPONSE, LogFields.TAGS);

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
        members.add(LogFields.USER_ID, event -> mdc(event).get(LogFields.MDC_USER_ID)).whenHasLength();
        members.add(LogFields.IP, event -> mdc(event).get(LogFields.MDC_CLIENT_IP)).whenHasLength();
        members.add(LogFields.REQUEST, event -> keyValue(event, LogFields.REQUEST)).whenNotNull();
        members.add(LogFields.RESPONSE, event -> keyValue(event, LogFields.RESPONSE)).whenNotNull();
        members.add(LogFields.TAGS, event -> tags(event, settings)).whenNotEmpty();
        members.add(LogFields.METADATA, event -> metadata(event, settings)).whenNotEmpty();
    }

    private static Map<String, String> mdc(ILoggingEvent event) {
        Map<String, String> properties = event.getMDCPropertyMap();
        return (properties != null) ? properties : Map.of();
    }

    private static Object keyValue(ILoggingEvent event, String key) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs == null) {
            return null;
        }
        for (KeyValuePair pair : pairs) {
            if (key.equals(pair.key)) {
                return pair.value;
            }
        }
        return null;
    }

    private static List<String> tags(ILoggingEvent event, JavalibsLogFormatSettings settings) {
        Set<String> tags = new LinkedHashSet<>(settings.tags());
        addSplitTags(tags, mdc(event).get(LogFields.MDC_TAGS));
        Object fromEvent = keyValue(event, LogFields.TAGS);
        if (fromEvent instanceof Collection<?> collection) {
            for (Object tag : collection) {
                addTag(tags, tag);
            }
        } else if (fromEvent != null) {
            addTag(tags, fromEvent);
        }
        return List.copyOf(tags);
    }

    private static void addSplitTags(Set<String> tags, String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return;
        }
        for (String tag : commaSeparated.split(",")) {
            addTag(tags, tag);
        }
    }

    private static void addTag(Set<String> tags, Object tag) {
        if (tag == null) {
            return;
        }
        String text = tag.toString().trim();
        if (!text.isEmpty()) {
            tags.add(text);
        }
    }

    private static Map<String, Object> metadata(ILoggingEvent event, JavalibsLogFormatSettings settings) {
        Map<String, Object> metadata = new LinkedHashMap<>(settings.metadata());
        mdc(event).forEach((key, value) -> {
            if (!LogFields.PROMOTED_MDC_KEYS.contains(key)) {
                metadata.put(key, value);
            }
        });
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            for (KeyValuePair pair : pairs) {
                if (!RESERVED_KEY_VALUES.contains(pair.key)) {
                    metadata.put(pair.key, pair.value);
                }
            }
        }
        return metadata;
    }
}
