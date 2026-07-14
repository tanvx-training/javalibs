package io.javalibs.security;

/**
 * Thrown when a bearer token is well-formed and correctly signed but its expiration time
 * (the {@code exp} claim, adjusted by the configured clock skew) has passed.
 */
public class ExpiredTokenException extends InvalidTokenException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message a human readable description
     */
    public ExpiredTokenException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message a human readable description
     * @param cause   the underlying cause (typically {@code io.jsonwebtoken.ExpiredJwtException})
     */
    public ExpiredTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
