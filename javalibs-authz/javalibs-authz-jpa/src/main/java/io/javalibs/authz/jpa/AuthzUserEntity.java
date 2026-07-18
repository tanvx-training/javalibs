package io.javalibs.authz.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA mapping of the {@code authz_user} table. */
@Entity
@Table(name = "authz_user")
public class AuthzUserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String username;

    @Column(length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Returns the primary key.
     *
     * @return the user id, or {@code null} if not yet persisted
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the primary key. Normally left {@code null} and assigned automatically
     * on first persist.
     *
     * @param id the user id
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the unique login username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the unique login username.
     *
     * @param username the username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the user's email address.
     *
     * @return the email, or {@code null} if not set
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the user's email address.
     *
     * @param email the email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the hashed password.
     *
     * @return the password hash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Sets the hashed password. Callers must never pass a plaintext password.
     *
     * @param passwordHash the password hash
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return the display name, or {@code null} if not set
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the human-readable display name.
     *
     * @param displayName the display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns whether the user account is enabled.
     *
     * @return {@code true} if the account is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the user account is enabled.
     *
     * @param enabled {@code true} to enable the account
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    /**
     * Returns the last-modified timestamp.
     *
     * @return the last-updated instant, in UTC
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last-modified timestamp. Normally managed automatically on update.
     *
     * @param updatedAt the last-updated instant
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
