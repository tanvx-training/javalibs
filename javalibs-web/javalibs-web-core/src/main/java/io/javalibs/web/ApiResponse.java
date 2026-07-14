package io.javalibs.web;

import java.time.Instant;

/**
 * Standard envelope for successful and failed API responses.
 *
 * <p>Every javalibs based service is expected to wrap its payloads in this envelope so that
 * clients can rely on a uniform shape: a {@code success} flag, the actual {@code data},
 * an optional human readable {@code message}, the server side {@code timestamp} and an
 * optional {@code traceId} used to correlate the response with server logs.</p>
 *
 * @param <T>       type of the payload carried by the response
 * @param success   {@code true} when the request was processed successfully
 * @param data      the payload, {@code null} for error responses
 * @param message   optional human readable message
 * @param timestamp the instant the response was created on the server
 * @param traceId   optional trace/correlation identifier, may be {@code null}
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp,
        String traceId) {

    /**
     * Creates a successful response wrapping the given payload.
     *
     * @param data the payload to return
     * @param <T>  payload type
     * @return a successful {@link ApiResponse} without message
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), null);
    }

    /**
     * Creates a successful response wrapping the given payload with a message.
     *
     * @param data    the payload to return
     * @param message human readable message
     * @param <T>     payload type
     * @return a successful {@link ApiResponse} with a message
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now(), null);
    }

    /**
     * Creates a failed response carrying only an error message.
     *
     * @param message human readable error message
     * @param <T>     payload type (always {@code null} for errors)
     * @return a failed {@link ApiResponse}
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now(), null);
    }

    /**
     * Creates a failed response carrying an error message and a trace identifier.
     *
     * @param message human readable error message
     * @param traceId trace/correlation identifier to help clients report the failure
     * @param <T>     payload type (always {@code null} for errors)
     * @return a failed {@link ApiResponse}
     */
    public static <T> ApiResponse<T> error(String message, String traceId) {
        return new ApiResponse<>(false, null, message, Instant.now(), traceId);
    }
}
