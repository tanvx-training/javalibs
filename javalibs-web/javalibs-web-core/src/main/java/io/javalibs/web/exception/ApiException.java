package io.javalibs.web.exception;

import io.javalibs.web.ErrorCode;

/**
 * Base runtime exception for API failures that map to a well-defined {@link ErrorCode}.
 *
 * <p>Throwing an {@code ApiException} (or a subclass) from any controller or service lets
 * the javalibs global exception handler translate it into a standard
 * {@code ErrorResponse} with the HTTP status carried by the error code.</p>
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Creates a new exception with the given error code and message.
     *
     * @param errorCode the error code describing the failure
     * @param message   human readable error message
     */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates a new exception with the given error code, message and cause.
     *
     * @param errorCode the error code describing the failure
     * @param message   human readable error message
     * @param cause     underlying cause
     */
    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the error code carried by this exception.
     *
     * @return the error code, never {@code null}
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
