package io.javalibs.security.issuer;

import java.time.Instant;
import java.util.Optional;

/**
 * SPI for persisting refresh token state. Implement this against your own storage (see
 * javalibs-authz-jpa) and wire it into {@link AuthenticationService}. Tokens are always
 * addressed by their hash ({@link RefreshTokens#hash(String)}); the raw token value is never
 * stored.
 */
public interface RefreshTokenStore {

    /**
     * Persists a newly issued refresh token record.
     *
     * @param record the record to save
     */
    void save(RefreshTokenRecord record);

    /**
     * Looks up a refresh token record by its hash.
     *
     * @param tokenHash the SHA-256 hex hash of the raw refresh token
     * @return the record, or empty when no such token exists
     */
    Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);

    /**
     * Marks a token as rotated: revokes it and records the hash of its replacement.
     *
     * @param tokenHash the hash of the token being rotated away from
     * @param rotatedToHash the hash of the newly issued replacement token
     * @param revokedAt the instant the rotation happened
     */
    void markRotated(String tokenHash, String rotatedToHash, Instant revokedAt);

    /**
     * Revokes a single token. Implementations must be a no-op (not throw) when the token
     * does not exist.
     *
     * @param tokenHash the hash of the token to revoke
     * @param revokedAt the instant of revocation
     */
    void revoke(String tokenHash, Instant revokedAt);

    /**
     * Revokes every still-active token belonging to a token family. Used for reuse
     * detection: presenting an already-rotated token is treated as evidence of theft, so
     * the entire family descended from the same login is killed.
     *
     * @param familyId the token family to revoke
     * @param revokedAt the instant of revocation
     */
    void revokeFamily(String familyId, Instant revokedAt);
}
