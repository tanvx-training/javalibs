package io.javalibs.logging.spring;

import java.util.List;

/**
 * Settings of the HTTP access log filter.
 *
 * <p>Body and header capture default to off. Recording payloads is a decision
 * about personal data, and it should be one an application makes deliberately
 * rather than one it inherits from a library.</p>
 *
 * @param includeHeaders   whether request headers are logged
 * @param includedHeaders  allowlist of header names to log; {@code null} or
 *                         empty falls back to {@link #DEFAULT_INCLUDED_HEADERS}
 * @param includeBody      whether request and response bodies are logged.
 *                         <strong>Response</strong> bodies are always buffered
 *                         in full by {@code ContentCachingResponseWrapper}
 *                         before {@code maxBodyLength} is applied — the cap
 *                         only limits what gets written to the log, not how
 *                         much memory the wrapper holds while the request is
 *                         in flight. Turning this on for a service with a
 *                         file-download or large-streaming endpoint can buffer
 *                         that entire payload in heap; leave it off for those
 *                         endpoints, or exclude their paths via
 *                         {@link #excludedPaths}. For SSE, a
 *                         {@code StreamingResponseBody}, or long-polling this
 *                         is not merely a memory concern but a functional
 *                         break at any payload size: Spring's
 *                         {@code ContentCachingResponseWrapper#flushBuffer()}
 *                         is an unconditional no-op (it compiles to a single
 *                         {@code return}), so no byte reaches the client until
 *                         the whole request completes. Excluding those
 *                         endpoints via {@link #excludedPaths} is mandatory,
 *                         not optional, once this is turned on
 * @param maxBodyLength    cap on characters kept from each body; values below
 *                         one fall back to {@link #DEFAULT_MAX_BODY_LENGTH}
 * @param excludedPaths    Ant patterns never logged; {@code null} falls back to
 *                         {@link #DEFAULT_EXCLUDED_PATHS}
 * @param trustProxy       whether {@code X-Forwarded-For} may be believed
 * @param slowThresholdMs  latency at or above which the event is logged at
 *                         {@code WARN}; {@code 0} disables the promotion
 */
public record AccessLogSettings(
        boolean includeHeaders,
        List<String> includedHeaders,
        boolean includeBody,
        int maxBodyLength,
        List<String> excludedPaths,
        boolean trustProxy,
        long slowThresholdMs) {

    /**
     * Headers logged by default — deliberately an allowlist, because the
     * dangerous headers ({@code Authorization}, {@code Cookie},
     * {@code X-Api-Key}) are exactly the ones a denylist tends to miss.
     */
    public static final List<String> DEFAULT_INCLUDED_HEADERS =
            List.of("Content-Type", "User-Agent", "Accept");

    /** Paths excluded from the access log by default. */
    public static final List<String> DEFAULT_EXCLUDED_PATHS = List.of("/actuator/**");

    /** Default cap on characters kept from one body. */
    public static final int DEFAULT_MAX_BODY_LENGTH = 2048;

    /** Canonical constructor applying the documented fallbacks. */
    public AccessLogSettings {
        includedHeaders = (includedHeaders == null || includedHeaders.isEmpty())
                ? DEFAULT_INCLUDED_HEADERS : List.copyOf(includedHeaders);
        excludedPaths = (excludedPaths == null) ? DEFAULT_EXCLUDED_PATHS : List.copyOf(excludedPaths);
        maxBodyLength = (maxBodyLength > 0) ? maxBodyLength : DEFAULT_MAX_BODY_LENGTH;
        slowThresholdMs = Math.max(slowThresholdMs, 0L);
    }

    /**
     * Returns the conservative defaults: no headers, no bodies, no proxy trust.
     *
     * @return the default settings
     */
    public static AccessLogSettings defaults() {
        return new AccessLogSettings(false, DEFAULT_INCLUDED_HEADERS, false,
                DEFAULT_MAX_BODY_LENGTH, DEFAULT_EXCLUDED_PATHS, false, 0L);
    }
}
