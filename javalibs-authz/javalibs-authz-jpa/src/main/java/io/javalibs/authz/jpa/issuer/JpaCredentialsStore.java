package io.javalibs.authz.jpa.issuer;

import io.javalibs.authz.jpa.AuthzUserEntity;
import io.javalibs.authz.jpa.AuthzUserRepository;
import io.javalibs.security.issuer.CredentialsStore;
import io.javalibs.security.issuer.StoredCredentials;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link CredentialsStore} backed by the {@code authz_user} table, so
 * javalibs-security-issuer can authenticate against the same user records that
 * javalibs-authz uses for permission grants.
 */
public class JpaCredentialsStore implements CredentialsStore {

    private final AuthzUserRepository userRepository;

    /**
     * Creates the store.
     *
     * @param userRepository the repository over {@code authz_user} rows
     */
    public JpaCredentialsStore(AuthzUserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredCredentials> findByUsername(String username) {
        return userRepository.findByUsername(username).map(JpaCredentialsStore::toCredentials);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredCredentials> findByUserId(String userId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return userRepository.findById(uuid).map(JpaCredentialsStore::toCredentials);
    }

    private static StoredCredentials toCredentials(AuthzUserEntity user) {
        return new StoredCredentials(user.getId().toString(), user.getUsername(),
                user.getEmail(), user.getPasswordHash(), user.isEnabled());
    }
}
