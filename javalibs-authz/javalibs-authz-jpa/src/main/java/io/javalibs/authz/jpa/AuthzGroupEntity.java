package io.javalibs.authz.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

/** JPA mapping of the {@code authz_group} table. */
@Entity
@Table(name = "authz_group")
public class AuthzGroupEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    /**
     * Returns the primary key.
     *
     * @return the group id, or {@code null} if not yet persisted
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the primary key. Normally left {@code null} and assigned automatically
     * on first persist.
     *
     * @param id the group id
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the unique group name.
     *
     * @return the group name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the unique group name.
     *
     * @param name the group name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the group description.
     *
     * @return the description, or {@code null} if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the group description.
     *
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
