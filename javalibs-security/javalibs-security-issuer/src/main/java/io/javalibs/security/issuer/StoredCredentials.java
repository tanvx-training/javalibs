package io.javalibs.security.issuer;

/**
 * A user's stored authentication credentials as read from the identity store.
 *
 * @param userId the stable user identifier (embedded in the access token's {@code sub} claim)
 * @param username the login username
 * @param email the user's email address (may be null)
 * @param passwordHash the hashed password, as produced by {@link PasswordHasher#hash(String)}
 * @param enabled whether the account is allowed to authenticate; disabled accounts are
 *     rejected by {@link AuthenticationService} on both login and refresh
 */
public record StoredCredentials(
        String userId, String username, String email, String passwordHash, boolean enabled) {
}
