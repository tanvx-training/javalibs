package io.javalibs.authz.jpa.issuer;

import io.javalibs.authz.jpa.AuthzRefreshTokenEntity;
import io.javalibs.authz.jpa.AuthzRefreshTokenRepository;
import io.javalibs.security.issuer.RefreshTokenRecord;
import io.javalibs.security.issuer.RefreshTokenStore;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link RefreshTokenStore} backed by the {@code authz_refresh_token} table.
 */
public class JpaRefreshTokenStore implements RefreshTokenStore {

    private final AuthzRefreshTokenRepository repository;

    /**
     * Creates the store.
     *
     * @param repository the repository over {@code authz_refresh_token} rows
     */
    public JpaRefreshTokenStore(AuthzRefreshTokenRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    @Transactional
    public void save(RefreshTokenRecord record) {
        AuthzRefreshTokenEntity entity = new AuthzRefreshTokenEntity();
        entity.setUserId(UUID.fromString(record.userId()));
        entity.setTokenHash(record.tokenHash());
        entity.setFamilyId(record.familyId());
        entity.setExpiresAt(record.expiresAt());
        entity.setRevokedAt(record.revokedAt());
        entity.setRotatedToHash(record.rotatedToHash());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(entity -> new RefreshTokenRecord(
                entity.getTokenHash(), entity.getUserId().toString(), entity.getFamilyId(),
                entity.getExpiresAt(), entity.getRevokedAt(), entity.getRotatedToHash()));
    }

    @Override
    @Transactional
    public boolean markRotated(String tokenHash, String rotatedToHash, Instant revokedAt) {
        int affectedRows = repository.markRotated(tokenHash, rotatedToHash, revokedAt);
        return affectedRows == 1;
    }

    @Override
    @Transactional
    public void revoke(String tokenHash, Instant revokedAt) {
        repository.findByTokenHash(tokenHash).ifPresent(entity -> {
            entity.setRevokedAt(revokedAt);
            repository.save(entity);
        });
    }

    @Override
    @Transactional
    public void revokeFamily(String familyId, Instant revokedAt) {
        repository.revokeFamily(familyId, revokedAt);
    }
}
