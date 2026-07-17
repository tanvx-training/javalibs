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
     * Revokes every still-active token belonging to the given family in a single statement.
     *
     * @param familyId the token family to revoke
     * @param revokedAt the instant of revocation
     * @return the number of rows updated
     */
    @Modifying
    @Transactional
    @Query("update AuthzRefreshTokenEntity t set t.revokedAt = :revokedAt "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);
}
