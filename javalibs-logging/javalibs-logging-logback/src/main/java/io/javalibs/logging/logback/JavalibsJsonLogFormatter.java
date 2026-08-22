package io.javalibs.logging.logback;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import io.javalibs.logging.LogFields;
import io.javalibs.logging.SensitiveKeys;
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

    /** Upper bound on the cause chain, so a cyclic or pathological chain cannot bloat a line. */
    private static final int MAX_ERROR_CHAIN = 10;

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
        members.add(LogFields.ERRORS, event -> errors(event, settings));
        members.add(LogFields.METADATA, event -> metadata(event, settings)).whenNotEmpty();
        if (settings.maskingEnabled()) {
            members.applyingValueProcessor(maskingProcessor(settings));
        }
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

    private static List<Map<String, Object>> errors(ILoggingEvent event,
            JavalibsLogFormatSettings settings) {
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable == null) {
            return List.of();
        }
        List<Map<String, Object>> errors = new ArrayList<>(2);
        Set<IThrowableProxy> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (throwable != null && errors.size() < MAX_ERROR_CHAIN && visited.add(throwable)) {
            Map<String, Object> error = new LinkedHashMap<>(3);
            error.put(LogFields.ERROR_TYPE, throwable.getClassName());
            error.put(LogFields.ERROR_MESSAGE, throwable.getMessage());
            if (settings.stacktraceEnabled()) {
                error.put(LogFields.ERROR_STACKTRACE,
                        stackTrace(throwable, settings.stacktraceMaxLength()));
            }
            errors.add(error);
            throwable = throwable.getCause();
        }
        return errors;
    }

    private static String stackTrace(IThrowableProxy throwable, int maxLength) {
        // Second line of defence: even if a negative length ever reached this
        // method, substring(0, maxLength) below must never throw.
        maxLength = Math.max(0, maxLength);
        StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
        if (frames == null || frames.length == 0) {
            return "";
        }
        StringBuilder rendered = new StringBuilder(256);
        for (StackTraceElementProxy frame : frames) {
            if (rendered.length() >= maxLength) {
                break;
            }
            rendered.append(frame.getSTEAsString()).append('\n');
        }
        return (rendered.length() > maxLength) ? rendered.substring(0, maxLength) : rendered.toString();
    }

    /**
     * Builds the processor that replaces every value sitting under a sensitive
     * field name, at any depth, with the configured mask.
     *
     * <p>The processor is typed on {@code Object} rather than {@code String} on
     * purpose: a secret written as a number — a PIN, a card number — would slip
     * through a {@code String} processor untouched.</p>
     */
    private static JsonWriter.ValueProcessor<Object> maskingProcessor(
            JavalibsLogFormatSettings settings) {
        SensitiveKeys keys = settings.sensitiveKeys();
        String mask = settings.mask();
        return JsonWriter.ValueProcessor.of(Object.class, (Object value) -> (Object) mask)
                .whenHasPath(path -> keys.isSensitive(path.name()));
    }
}
