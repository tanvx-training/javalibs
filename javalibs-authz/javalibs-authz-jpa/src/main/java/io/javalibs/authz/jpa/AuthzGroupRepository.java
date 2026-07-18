package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzGroupEntity}. */
public interface AuthzGroupRepository extends JpaRepository<AuthzGroupEntity, UUID> {

    /**
     * Finds a group by its unique name.
     *
     * @param name the group name
     * @return the matching group, or empty if none exists
     */
    Optional<AuthzGroupEntity> findByName(String name);
}
