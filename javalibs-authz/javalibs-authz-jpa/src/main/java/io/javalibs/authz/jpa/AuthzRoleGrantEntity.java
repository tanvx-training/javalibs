package io.javalibs.authz.jpa;

import io.javalibs.authz.SubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * JPA mapping of the {@code authz_role_grant} table. An empty {@code scopeType}/{@code scopeId}
 * pair means the grant is global (the columns are NOT NULL with '' as the global sentinel so
 * the unique constraint applies to global grants too).
 */
@Entity
@Table(name = "authz_role_grant",
        uniqueConstraints = @UniqueConstraint(name = "uq_authz_role_grant",
                columnNames = {"subject_type", "subject_id", "role_id", "scope_type", "scope_id"}))
public class AuthzRoleGrantEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 10)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private AuthzRoleEntity role;

    @Column(name = "scope_type", nullable = false, length = 100)
    private String scopeType = "";

    @Column(name = "scope_id", nullable = false, length = 100)
    private String scopeId = "";

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    /**
     * Returns the primary key.
     *
     * @return the grant id, or {@code null} if not yet persisted
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the primary key. Normally left {@code null} and assigned automatically
     * on first persist.
     *
     * @param id the grant id
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the kind of subject this grant applies to.
     *
     * @return the subject type
     */
    public SubjectType getSubjectType() {
        return subjectType;
    }

    /**
     * Sets the kind of subject this grant applies to.
     *
     * @param subjectType the subject type
     */
    public void setSubjectType(SubjectType subjectType) {
        this.subjectType = subjectType;
    }

    /**
     * Returns the stable identifier of the subject (user id or group id).
     *
     * @return the subject id
     */
    public String getSubjectId() {
        return subjectId;
    }

    /**
     * Sets the stable identifier of the subject (user id or group id).
     *
     * @param subjectId the subject id
     */
    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    /**
     * Returns the granted role.
     *
     * @return the role
     */
    public AuthzRoleEntity getRole() {
        return role;
    }

    /**
     * Sets the granted role.
     *
     * @param role the role
     */
    public void setRole(AuthzRoleEntity role) {
        this.role = role;
    }

    /**
     * Returns the scope type, or an empty string if this grant is global.
     *
     * @return the scope type
     */
    public String getScopeType() {
        return scopeType;
    }

    /**
     * Sets the scope type. Use an empty string (the default) for a global grant.
     *
     * @param scopeType the scope type
     */
    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    /**
     * Returns the scope id, or an empty string if this grant is global.
     *
     * @return the scope id
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * Sets the scope id. Use an empty string (the default) for a global grant.
     *
     * @param scopeId the scope id
     */
    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }
}
