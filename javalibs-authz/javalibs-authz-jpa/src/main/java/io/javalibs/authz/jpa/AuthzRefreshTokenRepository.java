package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzRefreshTokenEntity}. */
public interface AuthzRefreshTokenRepository extends JpaRepository<AuthzRefreshTokenEntity, UUID> {

    /**
     * Finds a refresh token record by the hash of its raw token value.
     *
     * @param tokenHash the SHA-256 hex hash of the raw refresh token
     * @return the matching record, or empty if none exists
     */
    Optional<AuthzRefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Atomically rotates a token: revokes it and records the replacement hash, but only when
     * the row is not already revoked. This is the conditional update that makes rotation
     * race-safe — of two concurrent callers presenting the same token, only one updates a row
     * (return value {@code 1}); the other gets {@code 0} and must treat it as reuse.
     *
     * @param tokenHash the hash of the token being rotated away from
     * @param rotatedToHash the hash of the newly issued replacement token
     * @param revokedAt the instant the rotation happened
     * @return the number of rows updated: {@code 1} when this call won the rotation, {@code 0}
     *     when the token was already revoked/rotated (or does not exist)
     * @note the bulk update clears the persistence context so subsequent lookups see fresh state
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update AuthzRefreshTokenEntity t set t.revokedAt = :revokedAt, "
            + "t.rotatedToHash = :rotatedToHash "
            + "where t.tokenHash = :tokenHash and t.revokedAt is null")
    int markRotated(@Param("tokenHash") String tokenHash,
            @Param("rotatedToHash") String rotatedToHash, @Param("revokedAt") Instant revokedAt);

    /**
     * Revokes every still-active token belonging to the given family in a single statement.
     *
     * @param familyId the token family to revoke
     * @param revokedAt the instant of revocation
     * @return the number of rows updated
     * @note the bulk update clears the persistence context so subsequent lookups see fresh state
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update AuthzRefreshTokenEntity t set t.revokedAt = :revokedAt "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);
}
