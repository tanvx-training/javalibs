package io.javalibs.security.issuer;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StoredCredentialsTest {

    @Test
    void fiveArgConstructorDefaultsRolesAndClaimsToEmpty() {
        StoredCredentials c = new StoredCredentials("u1", "alice", "a@x.io", "{bcrypt}h", true);
        assertThat(c.roles()).isEmpty();
        assertThat(c.extraClaims()).isEmpty();
    }

    @Test
    void nullRolesAndClaimsAreNormalizedToEmpty() {
        StoredCredentials c = new StoredCredentials("u1", "alice", "a@x.io", "{bcrypt}h", true,
                null, null);
        assertThat(c.roles()).isEmpty();
        assertThat(c.extraClaims()).isEmpty();
    }

    @Test
    void rolesAndClaimsRoundTripAndAreDefensivelyCopied() {
        Set<String> roles = new java.util.HashSet<>(Set.of("ADMIN", "USER"));
        Map<String, Object> claims = new java.util.HashMap<>(Map.of("tenant", "t1"));
        StoredCredentials c = new StoredCredentials("u1", "alice", "a@x.io", "{bcrypt}h", true,
                roles, claims);

        roles.clear();
        claims.clear();

        assertThat(c.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(c.extraClaims()).containsEntry("tenant", "t1");
    }
}
