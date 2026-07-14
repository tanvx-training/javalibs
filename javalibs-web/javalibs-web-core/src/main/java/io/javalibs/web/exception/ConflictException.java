package io.javalibs.web.exception;

import io.javalibs.web.CommonErrorCode;

/**
 * Thrown when a request conflicts with the current state of a resource, e.g. duplicate
 * creation or optimistic-locking failures. Maps to HTTP 409 via
 * {@link CommonErrorCode#CONFLICT}.
 */
public class ConflictException extends ApiException {

    /**
     * Creates a new conflict exception.
     *
     * @param message human readable error message
     */
    public ConflictException(String message) {
        super(CommonErrorCode.CONFLICT, message);
    }

    /**
     * Creates a new conflict exception with a cause.
     *
     * @param message human readable error message
     * @param cause   underlying cause
     */
    public ConflictException(String message, Throwable cause) {
        super(CommonErrorCode.CONFLICT, message, cause);
    }
}
