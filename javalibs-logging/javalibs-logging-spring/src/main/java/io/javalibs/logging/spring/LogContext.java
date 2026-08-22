package io.javalibs.logging.spring;

import java.util.LinkedHashSet;
import java.util.Set;

import io.javalibs.logging.LogFields;
import org.slf4j.MDC;

/**
 * Attaches tags and metadata to every log line emitted inside a scope.
 *
 * <p>Use it for facts that belong to a whole unit of work rather than to a
 * single line:</p>
 *
 * <pre>
 * try (LogContext.Scope scope = LogContext.tags("checkout")) {
 *     log.info("Cart validated");   // carries tag "checkout"
 *     charge(order);                // so does everything it logs
 * }
 * </pre>
 *
 * <p>For a single line prefer the standard SLF4J fluent API —
 * {@code log.atInfo().addKeyValue("orderId", id).log("...")} — which the
 * formatter renders into {@code metadata} without any javalibs-specific
 * call.</p>
 *
 * <p>Closing a scope restores whatever the entry held before, rather than
 * clearing it, so nested scopes and thread-pool reuse cannot leak or lose
 * context.</p>
 */
public final class LogContext {

    private LogContext() {
        // static utility
    }

    /**
     * Adds tags for the duration of the returned scope, merging them with any
     * tags already in scope.
     *
     * @param tags the tags to add; {@code null} and blank entries are ignored
     * @return a scope restoring the previous tags when closed
     */
    public static Scope tags(String... tags) {
        String previous = MDC.get(LogFields.MDC_TAGS);
        Set<String> merged = new LinkedHashSet<>();
        addAll(merged, previous);
        if (tags != null) {
            for (String tag : tags) {
                add(merged, tag);
            }
        }
        MDC.put(LogFields.MDC_TAGS, String.join(",", merged));
        return new Scope(LogFields.MDC_TAGS, previous);
    }

    /**
     * Sets an MDC entry for the duration of the returned scope. The formatter
     * renders unclaimed MDC entries into the {@code metadata} object.
     *
     * @param key   the MDC key, must not be {@code null}
     * @param value the value; {@code null} removes the entry within the scope
     * @return a scope restoring the previous value when closed
     */
    public static Scope put(String key, String value) {
        String previous = MDC.get(key);
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
        return new Scope(key, previous);
    }

    private static void addAll(Set<String> target, String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return;
        }
        for (String tag : commaSeparated.split(",")) {
            add(target, tag);
        }
    }

    private static void add(Set<String> target, String tag) {
        if (tag == null) {
            return;
        }
        String trimmed = tag.trim();
        if (!trimmed.isEmpty()) {
            target.add(trimmed);
        }
    }

    /** Restores the MDC entry it replaced. */
    public static final class Scope implements AutoCloseable {

        private final String key;
        private final String previous;

        private Scope(String key, String previous) {
            this.key = key;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        }
    }
}
