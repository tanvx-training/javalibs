package io.javalibs.security;

import java.time.Instant;

/**
 * Revocation list for JWTs.
 *
 * <p>Stateless JWTs cannot be invalidated before they expire — a critical gap
 * when a user changes their password, logs out everywhere or gets locked. A
 * {@code TokenBlacklist} closes it: on such events the identity service revokes
 * the token id ({@code jti} claim), and every resource service consults the
 * blacklist after signature validation. Entries only need to live until the
 * token's natural expiry, so the store stays small.</p>
 *
 * <p>Implementations: {@link InMemoryTokenBlacklist} (single instance only) and
 * {@code RedisTokenBlacklist} in javalibs-security-spring (shared across
 * instances — the right choice for microservices).</p>
 */
public interface TokenBlacklist {

    /**
     * {@link UserContext#attributes()} key under which the token id ({@code jti}
     * claim) is exposed by {@link JwtTokenValidator}. Tokens without a {@code jti}
     * cannot be revoked individually — always mint tokens with one.
     */
    String TOKEN_ID_ATTRIBUTE = "jti";

    /**
     * Revokes a token until its natural expiry.
     *
     * @param tokenId   the {@code jti} claim of the token to revoke
     * @param expiresAt the token's expiry; the entry may be dropped afterwards
     */
    void revoke(String tokenId, Instant expiresAt);

    /**
     * Returns whether the token id has been revoked (and the revocation is still
     * relevant, i.e. the token has not naturally expired yet).
     *
     * @param tokenId the {@code jti} claim to check
     * @return {@code true} when the token must be rejected
     */
    boolean isRevoked(String tokenId);
}
