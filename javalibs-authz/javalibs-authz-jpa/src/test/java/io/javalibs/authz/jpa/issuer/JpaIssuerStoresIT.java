package io.javalibs.authz.jpa.issuer;

import io.javalibs.authz.jpa.AuthzRefreshTokenRepository;
import io.javalibs.authz.jpa.AuthzUserRepository;
import io.javalibs.security.issuer.RefreshTokenRecord;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaIssuerStoresIT extends BaseIntegrationTest {

    @Autowired AuthzUserRepository users;
    @Autowired AuthzRefreshTokenRepository tokens;

    private UUID createUser(String username) {
        var user = new io.javalibs.authz.jpa.AuthzUserEntity();
        user.setUsername(username);
        user.setEmail(username + "@x.io");
        user.setPasswordHash("{bcrypt}h");
        return users.save(user).getId();
    }

    @Test
    void credentialsStoreFindsByUsernameAndUserId() {
        UUID id = createUser("store-alice");
        JpaCredentialsStore store = new JpaCredentialsStore(users);

        var byName = store.findByUsername("store-alice").orElseThrow();
        assertThat(byName.userId()).isEqualTo(id.toString());
        assertThat(byName.passwordHash()).isEqualTo("{bcrypt}h");
        assertThat(store.findByUserId(id.toString())).isPresent();
        assertThat(store.findByUserId("not-a-uuid")).isEmpty();
        assertThat(store.findByUsername("ghost")).isEmpty();
    }

    @Test
    void refreshTokenStoreRoundTripRotateAndRevokeFamily() {
        UUID id = createUser("store-bob");
        JpaRefreshTokenStore store = new JpaRefreshTokenStore(tokens);
        Instant expires = Instant.now().plus(30, ChronoUnit.DAYS);

        store.save(new RefreshTokenRecord("hash-1", id.toString(), "fam-1", expires, null, null));
        store.save(new RefreshTokenRecord("hash-2", id.toString(), "fam-1", expires, null, null));

        assertThat(store.findByTokenHash("hash-1")).isPresent();

        Instant now = Instant.now();
        store.markRotated("hash-1", "hash-2", now);
        RefreshTokenRecord rotated = store.findByTokenHash("hash-1").orElseThrow();
        assertThat(rotated.revokedAt()).isNotNull();
        assertThat(rotated.rotatedToHash()).isEqualTo("hash-2");

        store.revokeFamily("fam-1", now);
        assertThat(store.findByTokenHash("hash-2").orElseThrow().revokedAt()).isNotNull();
    }
}
