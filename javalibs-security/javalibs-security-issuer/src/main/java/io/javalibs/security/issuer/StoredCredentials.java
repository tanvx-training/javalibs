package io.javalibs.security.issuer;

import java.util.Map;
import java.util.Set;

/**
 * A user's stored authentication credentials as read from the identity store.
 *
 * @param userId the stable user identifier (embedded in the access token's {@code sub} claim)
 * @param username the login username
 * @param email the user's email address (may be null)
 * @param passwordHash the hashed password, as produced by {@link PasswordHasher#hash(String)}
 * @param enabled whether the account is allowed to authenticate; disabled accounts are
 *     rejected by {@link AuthenticationService} on both login and refresh
 * @param roles the roles written into the access token's roles claim; never null (empty when
 *     omitted)
 * @param extraClaims additional custom claims written into the access token; never null
 *     (empty when omitted). Reserved and already-mapped claim names are ignored by
 *     {@link TokenIssuer}.
 */
public record StoredCredentials(
        String userId, String username, String email, String passwordHash, boolean enabled,
        Set<String> roles, Map<String, Object> extraClaims) {

    public StoredCredentials {
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);
        extraClaims = (extraClaims == null) ? Map.of() : Map.copyOf(extraClaims);
    }

    /**
     * Convenience constructor for a credential without roles or custom claims.
     *
     * @param userId the stable user identifier
     * @param username the login username
     * @param email the user's email address (may be null)
     * @param passwordHash the hashed password
     * @param enabled whether the account may authenticate
     */
    public StoredCredentials(String userId, String username, String email, String passwordHash,
            boolean enabled) {
        this(userId, username, email, passwordHash, enabled, Set.of(), Map.of());
    }
}
