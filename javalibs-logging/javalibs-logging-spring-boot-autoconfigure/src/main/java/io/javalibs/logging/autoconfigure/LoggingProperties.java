package io.javalibs.logging.autoconfigure;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for javalibs structured logging, bound from the
 * {@code javalibs.logging.*} namespace.
 *
 * <p>These bindings drive the access log filter. The log formatter itself
 * cannot use them — it is created before the application context exists — and
 * reads the same properties straight from the {@code Environment}. Keep the two
 * sets of defaults in step: they describe one feature, not two.</p>
 *
 * @param json        JSON console output settings
 * @param service     value of the {@code service} field; defaults to
 *                    {@code spring.application.name}
 * @param host        value of the {@code host} field; defaults to the machine name
 * @param environment written to {@code metadata.env}
 * @param version     written to {@code metadata.version}
 * @param metadata    extra metadata attached to every log event
 * @param tags        tags attached to every log event
 * @param masking     sensitive data masking settings
 * @param stacktrace  stack trace rendering settings
 * @param access      HTTP access log settings
 */
@ConfigurationProperties("javalibs.logging")
public record LoggingProperties(
        @DefaultValue Json json,
        String service,
        String host,
        String environment,
        String version,
        @DefaultValue Map<String, String> metadata,
        @DefaultValue List<String> tags,
        @DefaultValue Masking masking,
        @DefaultValue Stacktrace stacktrace,
        @DefaultValue Access access) {

    /** Canonical constructor normalizing absent collections to empty ones. */
    public LoggingProperties {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
        tags = (tags == null) ? List.of() : List.copyOf(tags);
    }

    /**
     * JSON console output.
     *
     * @param enabled whether log output is rendered as javalibs JSON records
     *                (default {@code false}, so a developer console is not
     *                turned into JSON merely by adding the starter)
     */
    public record Json(@DefaultValue("false") boolean enabled) {
    }

    /**
     * Sensitive data masking.
     *
     * @param enabled whether masking is applied (default {@code true})
     * @param keys    extra field names to mask, <em>added to</em> the built-in
     *                denylist rather than replacing it
     * @param value   replacement text (default {@code ********})
     */
    public record Masking(
            @DefaultValue("true") boolean enabled,
            @DefaultValue List<String> keys,
            @DefaultValue("********") String value) {

        /** Canonical constructor normalizing absent key lists. */
        public Masking {
            keys = (keys == null) ? List.of() : List.copyOf(keys);
        }
    }

    /**
     * Stack trace rendering inside {@code errors[]}.
     *
     * @param enabled   whether stack traces are written (default {@code true})
     * @param maxLength cap on characters per stack trace (default {@code 4096})
     */
    public record Stacktrace(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("4096") int maxLength) {
    }

    /**
     * HTTP access log.
     *
     * @param enabled          whether the filter is registered (default {@code true})
     * @param includeHeaders   whether request headers are logged (default {@code false})
     * @param includedHeaders  allowlist of header names
     * @param includeBody      whether bodies are logged (default {@code false})
     * @param maxBodyLength    cap on characters per body (default {@code 2048})
     * @param excludedPaths    Ant patterns never logged (default {@code /actuator/**})
     * @param trustProxy       whether {@code X-Forwarded-For} may be believed
     *                         (default {@code false} — it is caller-controlled)
     * @param slowThresholdMs  latency at or above which the event is logged at
     *                         {@code WARN}; {@code 0} disables the promotion
     */
    public record Access(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("false") boolean includeHeaders,
            @DefaultValue({"Content-Type", "User-Agent", "Accept"}) List<String> includedHeaders,
            @DefaultValue("false") boolean includeBody,
            @DefaultValue("2048") int maxBodyLength,
            @DefaultValue("/actuator/**") List<String> excludedPaths,
            @DefaultValue("false") boolean trustProxy,
            @DefaultValue("0") long slowThresholdMs) {
    }
}
