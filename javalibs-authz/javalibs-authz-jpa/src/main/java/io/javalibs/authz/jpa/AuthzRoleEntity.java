package io.javalibs.authz.jpa;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** JPA mapping of the {@code authz_role} table with its {@code authz_role_permission} codes. */
@Entity
@Table(name = "authz_role")
public class AuthzRoleEntity {

    @Id
    private UUID id;

    @Column(name = "role_key", nullable = false, unique = true, length = 150)
    private String roleKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "authz_role_permission",
            joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 150)
    private Set<String> permissions = new LinkedHashSet<>();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    /**
     * Returns the primary key.
     *
     * @return the role id, or {@code null} if not yet persisted
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the primary key. Normally left {@code null} and assigned automatically
     * on first persist.
     *
     * @param id the role id
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the unique, stable role key used for lookups (e.g. {@code "issue-viewer"}).
     *
     * @return the role key
     */
    public String getRoleKey() {
        return roleKey;
    }

    /**
     * Sets the unique, stable role key.
     *
     * @param roleKey the role key
     */
    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    /**
     * Returns the human-readable role name.
     *
     * @return the role name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the human-readable role name.
     *
     * @param name the role name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the role description.
     *
     * @return the description, or {@code null} if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the role description.
     *
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the permission codes granted by this role.
     *
     * @return the permission codes
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * Replaces the permission codes granted by this role.
     *
     * @param permissions the permission codes
     */
    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }
}
