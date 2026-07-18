package io.javalibs.security.issuer;

import java.time.Instant;

/**
 * Persisted state of a single refresh token, stored by its hash (see
 * {@link RefreshTokens#hash(String)}), never by the raw token value.
 *
 * <p>Refresh tokens rotate on every use: each token belongs to a {@code familyId} shared by
 * all tokens descended from the same login. When a token is rotated, its record is marked
 * revoked and points at the hash of its replacement via {@code rotatedToHash}. If a rotated
 * (and therefore revoked) token is presented again, that is treated as evidence of token
 * theft and the whole family is revoked — see {@link AuthenticationService#refresh(String)}.
 *
 * @param tokenHash the SHA-256 hex hash of the raw refresh token
 * @param userId the stable user id this token was issued to
 * @param familyId the id shared by every token descended from the same login
 * @param expiresAt when this token naturally expires
 * @param revokedAt when this token was revoked (by rotation, reuse detection, or logout);
 *     null while the token is still active
 * @param rotatedToHash the hash of the token this one was rotated into; null unless the
 *     token has been rotated
 */
public record RefreshTokenRecord(
        String tokenHash,
        String userId,
        String familyId,
        Instant expiresAt,
        Instant revokedAt,
        String rotatedToHash) {

    /**
     * Returns whether this token has been revoked (by rotation, reuse detection, or logout).
     *
     * @return true when {@code revokedAt} is set
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Returns whether this token has passed its natural expiry.
     *
     * @param now the current instant
     * @return true when {@code expiresAt} is before {@code now}
     */
    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    /**
     * Returns whether this token has already been rotated into a replacement.
     *
     * @return true when {@code rotatedToHash} is set
     */
    public boolean wasRotated() {
        return rotatedToHash != null;
    }
}
