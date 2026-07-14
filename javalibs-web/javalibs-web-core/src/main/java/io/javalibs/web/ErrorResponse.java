package io.javalibs.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard error body returned by javalibs based services for every failed request.
 *
 * @param timestamp   the instant the error response was created
 * @param status      HTTP status code
 * @param code        machine-readable error code, see {@link ErrorCode}
 * @param message     human readable error message
 * @param path        request path that produced the error
 * @param traceId     optional trace/correlation identifier, may be {@code null}
 * @param fieldErrors per-field validation violations, empty when not applicable
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldViolation> fieldErrors) {

    /**
     * Canonical constructor normalizing {@code fieldErrors} to an immutable, never
     * {@code null} list.
     */
    public ErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /**
     * A single field-level validation violation.
     *
     * @param field   name/path of the offending field
     * @param message human readable violation message
     */
    public record FieldViolation(String field, String message) {
    }

    /**
     * Creates a new {@link Builder} with the timestamp preset to {@link Instant#now()}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ErrorResponse}.
     */
    public static final class Builder {

        private Instant timestamp = Instant.now();
        private int status;
        private String code;
        private String message;
        private String path;
        private String traceId;
        private final List<FieldViolation> fieldErrors = new ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the response timestamp (defaults to {@link Instant#now()}).
         *
         * @param timestamp the timestamp to use
         * @return this builder
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the HTTP status code.
         *
         * @param status HTTP status code
         * @return this builder
         */
        public Builder status(int status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the machine-readable error code.
         *
         * @param code error code string
         * @return this builder
         */
        public Builder code(String code) {
            this.code = code;
            return this;
        }

        /**
         * Sets both the HTTP status and the code from the given {@link ErrorCode}.
         *
         * @param errorCode the error code to derive status and code from
         * @return this builder
         */
        public Builder errorCode(ErrorCode errorCode) {
            this.status = errorCode.httpStatus();
            this.code = errorCode.code();
            return this;
        }

        /**
         * Sets the human readable error message.
         *
         * @param message error message
         * @return this builder
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Sets the request path that produced the error.
         *
         * @param path request path
         * @return this builder
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the trace/correlation identifier.
         *
         * @param traceId trace identifier, may be {@code null}
         * @return this builder
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * Replaces the field violations with the given list.
         *
         * @param fieldErrors field violations, may be {@code null} for none
         * @return this builder
         */
        public Builder fieldErrors(List<FieldViolation> fieldErrors) {
            this.fieldErrors.clear();
            if (fieldErrors != null) {
                this.fieldErrors.addAll(fieldErrors);
            }
            return this;
        }

        /**
         * Adds a single field violation.
         *
         * @param field   offending field name
         * @param message violation message
         * @return this builder
         */
        public Builder fieldError(String field, String message) {
            this.fieldErrors.add(new FieldViolation(field, message));
            return this;
        }

        /**
         * Builds the immutable {@link ErrorResponse}.
         *
         * @return the built error response
         */
        public ErrorResponse build() {
            return new ErrorResponse(timestamp, status, code, message, path, traceId, fieldErrors);
        }
    }
}
