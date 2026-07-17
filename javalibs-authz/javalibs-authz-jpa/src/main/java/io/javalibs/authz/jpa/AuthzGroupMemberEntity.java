package io.javalibs.authz.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** JPA mapping of the {@code authz_group_member} table (direct user-group membership). */
@Entity
@Table(name = "authz_group_member",
        uniqueConstraints = @UniqueConstraint(name = "uq_authz_group_member",
                columnNames = {"group_id", "user_id"}))
public class AuthzGroupMemberEntity {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    /**
     * Returns the primary key.
     *
     * @return the membership id, or {@code null} if not yet persisted
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the primary key. Normally left {@code null} and assigned automatically
     * on first persist.
     *
     * @param id the membership id
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the id of the group the user belongs to.
     *
     * @return the group id
     */
    public UUID getGroupId() {
        return groupId;
    }

    /**
     * Sets the id of the group the user belongs to.
     *
     * @param groupId the group id
     */
    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    /**
     * Returns the id of the member user.
     *
     * @return the user id
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * Sets the id of the member user.
     *
     * @param userId the user id
     */
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
