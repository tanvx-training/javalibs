package io.javalibs.security;

/**
 * Thrown when a bearer token cannot be validated: it is malformed, has an invalid signature,
 * fails an issuer/audience check, or is otherwise unusable.
 *
 * <p>{@link ExpiredTokenException} is a dedicated subclass for the (very common) expiry case,
 * so callers may distinguish "expired" from "broken" while still catching a single type.</p>
 */
public class InvalidTokenException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message a human readable description of the validation failure
     */
    public InvalidTokenException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message a human readable description of the validation failure
     * @param cause   the underlying cause (typically a JJWT exception)
     */
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
