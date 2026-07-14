package io.javalibs.security;

/**
 * Thrown when a structurally valid token has been revoked through a
 * {@link TokenBlacklist} (logout-everywhere, password change, account lock).
 */
public class RevokedTokenException extends InvalidTokenException {

    public RevokedTokenException(String message) {
        super(message);
    }
}
