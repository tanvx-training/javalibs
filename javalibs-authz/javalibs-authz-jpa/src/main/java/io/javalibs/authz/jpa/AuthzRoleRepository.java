package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzRoleEntity}. */
public interface AuthzRoleRepository extends JpaRepository<AuthzRoleEntity, UUID> {

    /**
     * Finds a role by its unique, stable role key.
     *
     * @param roleKey the role key
     * @return the matching role, or empty if none exists
     */
    Optional<AuthzRoleEntity> findByRoleKey(String roleKey);

    /**
     * Returns every role together with its permission codes, fetched eagerly to avoid
     * N+1 queries.
     *
     * @return all roles with permissions loaded
     */
    @Query("select distinct r from AuthzRoleEntity r left join fetch r.permissions")
    List<AuthzRoleEntity> findAllWithPermissions();
}
