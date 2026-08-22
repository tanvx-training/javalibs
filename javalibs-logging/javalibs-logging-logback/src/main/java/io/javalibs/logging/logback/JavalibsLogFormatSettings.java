package io.javalibs.logging.logback;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.javalibs.logging.LogHost;
import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Snapshot of the {@code javalibs.logging.*} configuration needed by
 * {@code JavalibsJsonLogFormatter}.
 *
 * <p>The formatter is built by Spring Boot while the logging system starts —
 * before any {@code ApplicationContext} exists — so configuration cannot arrive
 * through {@code @ConfigurationProperties} beans. It is read straight from the
 * {@link Environment} once, here, and cached for the lifetime of the formatter
 * so that no property lookup happens per log line.</p>
 */
public final class JavalibsLogFormatSettings {

    /** Default cap on the number of characters kept from one stack trace. */
    public static final int DEFAULT_STACKTRACE_MAX_LENGTH = 4096;

    private final String service;
    private final String host;
    private final List<String> tags;
    private final Map<String, String> metadata;
    private final SensitiveKeys sensitiveKeys;
    private final String mask;
    private final boolean maskingEnabled;
    private final boolean stacktraceEnabled;
    private final int stacktraceMaxLength;

    private JavalibsLogFormatSettings(String service, String host, List<String> tags,
            Map<String, String> metadata, SensitiveKeys sensitiveKeys, String mask,
            boolean maskingEnabled, boolean stacktraceEnabled, int stacktraceMaxLength) {
        this.service = service;
        this.host = host;
        this.tags = List.copyOf(tags);
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.sensitiveKeys = sensitiveKeys;
        this.mask = mask;
        this.maskingEnabled = maskingEnabled;
        this.stacktraceEnabled = stacktraceEnabled;
        this.stacktraceMaxLength = stacktraceMaxLength;
    }

    /**
     * Reads the settings from the environment, applying every documented default.
     *
     * @param environment the application environment, must not be {@code null}
     * @return an immutable settings snapshot
     */
    public static JavalibsLogFormatSettings from(Environment environment) {
        Binder binder = Binder.get(environment);

        String service = firstNonBlank(environment.getProperty("javalibs.logging.service"),
                environment.getProperty("spring.application.name"));
        String host = firstNonBlank(environment.getProperty("javalibs.logging.host"), LogHost.current());

        List<String> tags = binder.bind("javalibs.logging.tags", Bindable.listOf(String.class))
                .orElseGet(List::of);

        Map<String, String> metadata = new LinkedHashMap<>(
                binder.bind("javalibs.logging.metadata", Bindable.mapOf(String.class, String.class))
                        .orElseGet(Map::of));
        putIfPresent(metadata, "env", environment.getProperty("javalibs.logging.environment"));
        putIfPresent(metadata, "version", environment.getProperty("javalibs.logging.version"));

        boolean maskingEnabled =
                environment.getProperty("javalibs.logging.masking.enabled", Boolean.class, Boolean.TRUE);
        List<String> extraKeys = binder.bind("javalibs.logging.masking.keys", Bindable.listOf(String.class))
                .orElseGet(List::of);
        SensitiveKeys sensitiveKeys =
                maskingEnabled ? SensitiveKeys.withAdditional(extraKeys) : SensitiveKeys.none();
        // A property present but blank ("javalibs.logging.masking.value=") must fall
        // back to the default too, matching SensitiveDataMasker's own guard — otherwise
        // the same configuration would mask JSON fields with an empty string while
        // form-encoded bodies still get "********".
        String configuredMask = environment.getProperty("javalibs.logging.masking.value");
        String mask = (configuredMask != null && !configuredMask.isBlank())
                ? configuredMask
                : SensitiveDataMasker.DEFAULT_MASK;

        boolean stacktraceEnabled =
                environment.getProperty("javalibs.logging.stacktrace.enabled", Boolean.class, Boolean.TRUE);
        // Bound rather than read via Environment#getProperty so that this
        // property is reachable by the same relaxed name matching
        // (max-length, maxLength, MAX_LENGTH, ...) as tags/metadata/masking.keys
        // above and as LoggingProperties#stacktrace().maxLength() below — a
        // plain getProperty() call only matches the exact key it names.
        int stacktraceMaxLength = binder.bind("javalibs.logging.stacktrace.max-length", Bindable.of(Integer.class))
                .orElse(DEFAULT_STACKTRACE_MAX_LENGTH);
        if (stacktraceMaxLength < 0) {
            // A negative value most likely means "no limit"; falling back to the
            // documented default keeps stack traces readable without silently
            // deleting them the way clamping to 0 would.
            stacktraceMaxLength = DEFAULT_STACKTRACE_MAX_LENGTH;
        }

        return new JavalibsLogFormatSettings(service, host, tags, metadata, sensitiveKeys, mask,
                maskingEnabled, stacktraceEnabled, stacktraceMaxLength);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    /** @return the value of the {@code service} field, may be {@code null} */
    public String service() {
        return service;
    }

    /** @return the value of the {@code host} field */
    public String host() {
        return host;
    }

    /** @return tags attached to every log event */
    public List<String> tags() {
        return tags;
    }

    /** @return metadata attached to every log event */
    public Map<String, String> metadata() {
        return metadata;
    }

    /** @return the sensitive key policy; matches nothing when masking is off */
    public SensitiveKeys sensitiveKeys() {
        return sensitiveKeys;
    }

    /** @return the replacement written in place of a secret */
    public String mask() {
        return mask;
    }

    /** @return whether masking is switched on */
    public boolean maskingEnabled() {
        return maskingEnabled;
    }

    /** @return whether stack traces are written into {@code errors[]} */
    public boolean stacktraceEnabled() {
        return stacktraceEnabled;
    }

    /**
     * @return the cap on characters kept from one stack trace; never negative —
     *         a negative configured value falls back to
     *         {@link #DEFAULT_STACKTRACE_MAX_LENGTH}
     */
    public int stacktraceMaxLength() {
        return stacktraceMaxLength;
    }
}
