package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzUserEntity}. */
public interface AuthzUserRepository extends JpaRepository<AuthzUserEntity, UUID> {

    /**
     * Finds a user by its unique login username.
     *
     * @param username the username
     * @return the matching user, or empty if none exists
     */
    Optional<AuthzUserEntity> findByUsername(String username);
}
