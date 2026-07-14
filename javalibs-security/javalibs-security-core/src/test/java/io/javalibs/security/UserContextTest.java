package io.javalibs.security;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link UserContext} immutability, role checks and the builder.
 */
class UserContextTest {

    @Test
    void nullCollectionsAreNormalizedToEmpty() {
        UserContext user = new UserContext("u1", null, null, null, null, null);

        assertThat(user.roles()).isNotNull().isEmpty();
        assertThat(user.attributes()).isNotNull().isEmpty();
    }

    @Test
    void collectionsAreDefensivelyCopied() {
        Set<String> roles = new HashSet<>(Set.of("ADMIN"));
        Map<String, Object> attributes = new HashMap<>(Map.of("k", "v"));

        UserContext user = new UserContext("u1", "name", null, roles, null, attributes);
        roles.add("HACKER");
        attributes.put("k2", "v2");

        assertThat(user.roles()).containsExactly("ADMIN");
        assertThat(user.attributes()).containsOnlyKeys("k");
    }

    @Test
    void exposedCollectionsAreUnmodifiable() {
        UserContext user = UserContext.builder()
                .userId("u1")
                .roles("ADMIN")
                .attribute("k", "v")
                .build();

        assertThatThrownBy(() -> user.roles().add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> user.attributes().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hasRoleMatchesExactly() {
        UserContext user = UserContext.builder().userId("u1").roles("ADMIN", "USER").build();

        assertThat(user.hasRole("ADMIN")).isTrue();
        assertThat(user.hasRole("admin")).isFalse();
        assertThat(user.hasRole(null)).isFalse();
    }

    @Test
    void hasAnyRoleMatchesAtLeastOne() {
        UserContext user = UserContext.builder().userId("u1").roles("USER").build();

        assertThat(user.hasAnyRole("ADMIN", "USER")).isTrue();
        assertThat(user.hasAnyRole("ADMIN", "AUDITOR")).isFalse();
        assertThat(user.hasAnyRole()).isFalse();
        assertThat(user.hasAnyRole((String[]) null)).isFalse();
    }

    @Test
    void builderPopulatesAllFields() {
        UserContext user = UserContext.builder()
                .userId("u1")
                .username("jdoe")
                .email("jdoe@example.com")
                .roles(Set.of("ADMIN"))
                .role("USER")
                .tenantId("acme")
                .attribute("dept", "eng")
                .build();

        assertThat(user.userId()).isEqualTo("u1");
        assertThat(user.username()).isEqualTo("jdoe");
        assertThat(user.email()).isEqualTo("jdoe@example.com");
        assertThat(user.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(user.tenantId()).isEqualTo("acme");
        assertThat(user.attributes()).containsEntry("dept", "eng");
    }
}
