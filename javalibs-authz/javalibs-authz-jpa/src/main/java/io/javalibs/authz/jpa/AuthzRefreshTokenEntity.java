package io.javalibs.authz.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA mapping of the {@code authz_refresh_token} table (hashed rotating refresh tokens). */
@Entity
@Table(name = "authz_refresh_token")
public class AuthzRefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "rotated_to_hash", length = 64)
    private String rotatedToHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    /**
     * Returns the primary key.
     *
     * @return the refresh token id, or {@code null} if not yet persisted
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the primary key. Normally left {@code null} and assigned automatically
     * on first persist.
     *
     * @param id the refresh token id
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the id of the user this token was issued to.
     *
     * @return the user id
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * Sets the id of the user this token was issued to.
     *
     * @param userId the user id
     */
    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    /**
     * Returns the SHA-256 hex hash of the raw refresh token.
     *
     * @return the token hash
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * Sets the SHA-256 hex hash of the raw refresh token.
     *
     * @param tokenHash the token hash
     */
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    /**
     * Returns the id shared by every token descended from the same login.
     *
     * @return the token family id
     */
    public String getFamilyId() {
        return familyId;
    }

    /**
     * Sets the id shared by every token descended from the same login.
     *
     * @param familyId the token family id
     */
    public void setFamilyId(String familyId) {
        this.familyId = familyId;
    }

    /**
     * Returns when this token naturally expires.
     *
     * @return the expiry instant
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets when this token naturally expires.
     *
     * @param expiresAt the expiry instant
     */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Returns when this token was revoked, if at all.
     *
     * @return the revocation instant, or {@code null} while the token is still active
     */
    public Instant getRevokedAt() {
        return revokedAt;
    }

    /**
     * Sets when this token was revoked.
     *
     * @param revokedAt the revocation instant
     */
    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    /**
     * Returns the hash of the token this one was rotated into, if any.
     *
     * @return the replacement token's hash, or {@code null} unless rotated
     */
    public String getRotatedToHash() {
        return rotatedToHash;
    }

    /**
     * Sets the hash of the token this one was rotated into.
     *
     * @param rotatedToHash the replacement token's hash
     */
    public void setRotatedToHash(String rotatedToHash) {
        this.rotatedToHash = rotatedToHash;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return the creation instant, in UTC
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp. Normally managed automatically on first persist.
     *
     * @param createdAt the creation instant
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
