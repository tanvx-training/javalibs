package io.javalibs.security.issuer;

/**
 * Thrown by {@link AuthenticationService#login(String, String)} when the username does not
 * exist, the password does not match, or the account is disabled. The message is
 * deliberately generic ("Invalid credentials") so callers cannot distinguish these cases
 * and enumerate valid usernames.
 */
public class InvalidCredentialsException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message the exception message
     */
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
