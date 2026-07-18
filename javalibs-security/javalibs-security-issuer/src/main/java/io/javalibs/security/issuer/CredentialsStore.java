package io.javalibs.security.issuer;

import java.util.Optional;

/**
 * SPI for reading stored user credentials. Implement this against your user table (see
 * javalibs-authz-jpa or your own schema) and wire it into {@link AuthenticationService}.
 */
public interface CredentialsStore {

    /**
     * Looks up a user's stored credentials by login username.
     *
     * @param username the login username
     * @return the stored credentials, or empty when no such user exists
     */
    Optional<StoredCredentials> findByUsername(String username);

    /**
     * Looks up a user's stored credentials by stable user id. Used by
     * {@link AuthenticationService#refresh(String)} to re-check the account's enabled state
     * without requiring the caller to resend a username.
     *
     * @param userId the stable user identifier
     * @return the stored credentials, or empty when no such user exists
     */
    Optional<StoredCredentials> findByUserId(String userId);
}
