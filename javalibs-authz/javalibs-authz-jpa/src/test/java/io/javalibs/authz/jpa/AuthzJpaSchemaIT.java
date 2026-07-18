package io.javalibs.authz.jpa;

import io.javalibs.authz.SubjectType;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzJpaSchemaIT extends BaseIntegrationTest {

    @Autowired AuthzUserRepository users;
    @Autowired AuthzRoleRepository roles;
    @Autowired AuthzRoleGrantRepository grants;

    @Test
    void persistsUserRoleAndGrantRoundTrip() {
        AuthzUserEntity user = new AuthzUserEntity();
        user.setUsername("alice");
        user.setPasswordHash("{bcrypt}x");
        users.save(user);

        AuthzRoleEntity role = new AuthzRoleEntity();
        role.setRoleKey("issue-viewer");
        role.setName("Issue viewer");
        role.setPermissions(Set.of("issue.read"));
        roles.save(role);

        AuthzRoleGrantEntity grant = new AuthzRoleGrantEntity();
        grant.setSubjectType(SubjectType.USER);
        grant.setSubjectId(user.getId().toString());
        grant.setRole(role);
        grant.setScopeType("project");
        grant.setScopeId("42");
        grants.save(grant);

        var loaded = grants.findWithRoleBySubject(SubjectType.USER, user.getId().toString());
        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().getRole().getPermissions()).containsExactly("issue.read");
        assertThat(users.findByUsername("alice")).isPresent();
    }
}
