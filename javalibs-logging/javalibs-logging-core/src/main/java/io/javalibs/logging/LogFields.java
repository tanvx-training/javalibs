package io.javalibs.logging;

import java.util.Set;

/**
 * Field names of the javalibs structured log record, plus the MDC keys javalibs
 * itself populates.
 *
 * <p>The names are part of the log contract consumed by log aggregation
 * pipelines: changing one is a breaking change for every dashboard and alert
 * built on it.</p>
 */
public final class LogFields {

    /** Event time, ISO-8601 in UTC with millisecond precision. */
    public static final String TIMESTAMP = "timestamp";
    /** Severity of the log event. */
    public static final String LEVEL = "level";
    /** Human readable description of the event. */
    public static final String MESSAGE = "message";
    /** Unique identifier of this single log entry. */
    public static final String LOG_ID = "log_id";
    /** Name of the service or application that emitted the event. */
    public static final String SERVICE = "service";
    /** Machine the event happened on. */
    public static final String HOST = "host";
    /** Identifier of the user the event relates to. */
    public static final String USER_ID = "user_id";
    /** IP address of the requester. */
    public static final String IP = "ip";
    /** Details of the incoming HTTP request. */
    public static final String REQUEST = "request";
    /** Details of the outgoing HTTP response. */
    public static final String RESPONSE = "response";
    /** Keywords used to filter and search logs. */
    public static final String TAGS = "tags";
    /** Errors attached to the event; an empty array when there are none. */
    public static final String ERRORS = "errors";
    /** Additional information about the environment or system. */
    public static final String METADATA = "metadata";

    /** {@code request.method} — HTTP method. */
    public static final String METHOD = "method";
    /** {@code request.endpoint} — request path including query string. */
    public static final String ENDPOINT = "endpoint";
    /** {@code request.headers} — selected request headers. */
    public static final String HEADERS = "headers";
    /** {@code request.body} — request payload. */
    public static final String BODY = "body";
    /** {@code response.status} — HTTP status code. */
    public static final String STATUS = "status";
    /** {@code response.latency_ms} — time taken to serve the request. */
    public static final String LATENCY_MS = "latency_ms";
    /** {@code response.response_body} — response payload. */
    public static final String RESPONSE_BODY = "response_body";

    /** {@code errors[].type} — fully qualified exception class name. */
    public static final String ERROR_TYPE = "type";
    /** {@code errors[].message} — exception message. */
    public static final String ERROR_MESSAGE = "message";
    /** {@code errors[].stacktrace} — rendered stack trace. */
    public static final String ERROR_STACKTRACE = "stacktrace";

    /** MDC key holding the authenticated user id. */
    public static final String MDC_USER_ID = "userId";
    /** MDC key holding the resolved client IP address. */
    public static final String MDC_CLIENT_IP = "clientIp";
    /** MDC key holding comma separated request-scoped tags. */
    public static final String MDC_TAGS = "logTags";

    /**
     * MDC keys that are already rendered as dedicated top-level fields and must
     * therefore not be repeated inside {@link #METADATA}.
     */
    public static final Set<String> PROMOTED_MDC_KEYS = Set.of(MDC_USER_ID, MDC_CLIENT_IP, MDC_TAGS);

    private LogFields() {
        // constants holder
    }
}
