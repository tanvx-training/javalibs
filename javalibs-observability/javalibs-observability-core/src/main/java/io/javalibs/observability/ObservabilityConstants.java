package io.javalibs.observability;

/**
 * Shared constants for MDC keys and common metric tag names used across the
 * javalibs observability modules.
 */
public final class ObservabilityConstants {

    /**
     * MDC key holding the current trace id.
     *
     * <p>This is the key Micrometer Tracing populates automatically when a
     * tracing bridge (for example {@code micrometer-tracing-bridge-otel}) is
     * on the classpath, making the trace id available to log patterns via
     * {@code %X{traceId}}.</p>
     */
    public static final String TRACE_ID = "traceId";

    /**
     * MDC key holding the current span id.
     *
     * <p>This is the key Micrometer Tracing populates automatically when a
     * tracing bridge is on the classpath, making the span id available to log
     * patterns via {@code %X{spanId}}.</p>
     */
    public static final String SPAN_ID = "spanId";

    /** Metric tag name carrying the application name ({@code spring.application.name}). */
    public static final String TAG_APPLICATION = "application";

    /** Metric tag name carrying the deployment environment (for example {@code prod}). */
    public static final String TAG_ENVIRONMENT = "environment";

    private ObservabilityConstants() {
        // constants holder
    }
}
