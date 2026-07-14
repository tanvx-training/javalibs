package io.javalibs.cqrs;

/**
 * Base runtime exception for all CQRS infrastructure errors raised by javalibs.
 */
public class CqrsException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message the detail message
     */
    public CqrsException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public CqrsException(String message, Throwable cause) {
        super(message, cause);
    }
}
