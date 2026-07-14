package io.javalibs.security;

/**
 * Validates an opaque bearer token and converts it into a {@link UserContext}.
 *
 * <p>Implementations must be thread-safe: a single instance is typically shared by all
 * request-processing threads of an application.</p>
 */
@FunctionalInterface
public interface TokenValidator {

    /**
     * Validates the given token and extracts the authenticated user's context.
     *
     * @param token the raw token value (without any {@code Bearer } prefix)
     * @return the user context extracted from the verified token, never {@code null}
     * @throws ExpiredTokenException if the token is well-formed and correctly signed but expired
     * @throws InvalidTokenException if the token is missing, malformed, tampered with or fails
     *                               any other validation rule (issuer, audience, signature, ...)
     */
    UserContext validate(String token) throws InvalidTokenException;
}
