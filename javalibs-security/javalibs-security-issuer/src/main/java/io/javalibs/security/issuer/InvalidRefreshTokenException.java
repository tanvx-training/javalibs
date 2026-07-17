package io.javalibs.security.issuer;

/**
 * Thrown by {@link AuthenticationService#refresh(String)} when the refresh token does not
 * exist, has expired, has been revoked, or belongs to a disabled account.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message the exception message
     */
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
