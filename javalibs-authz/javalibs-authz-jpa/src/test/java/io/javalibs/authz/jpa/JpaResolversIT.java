package io.javalibs.authz.jpa;

import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.authz.SubjectType;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JpaResolversIT extends BaseIntegrationTest {

    @Autowired AuthzUserRepository users;
    @Autowired AuthzGroupRepository groups;
    @Autowired AuthzGroupMemberRepository members;
    @Autowired AuthzRoleRepository roles;
    @Autowired AuthzRoleGrantRepository grants;

    private AuthzUserEntity newUser(String username) {
        AuthzUserEntity user = new AuthzUserEntity();
        user.setUsername(username);
        user.setPasswordHash("{bcrypt}x");
        return users.save(user);
    }

    private AuthzRoleEntity newRole(String key, Set<String> permissions) {
        AuthzRoleEntity role = new AuthzRoleEntity();
        role.setRoleKey(key);
        role.setName(key);
        role.setPermissions(permissions);
        return roles.save(role);
    }

    private void grant(SubjectType type, String subjectId, AuthzRoleEntity role,
            String scopeType, String scopeId) {
        AuthzRoleGrantEntity grant = new AuthzRoleGrantEntity();
        grant.setSubjectType(type);
        grant.setSubjectId(subjectId);
        grant.setRole(role);
        grant.setScopeType(scopeType);
        grant.setScopeId(scopeId);
        grants.save(grant);
    }

    @Test
    void resolvesScopedAndGlobalGrants() {
        AuthzUserEntity user = newUser("res-user");
        AuthzRoleEntity viewer = newRole("res-viewer", Set.of("issue.read"));
        AuthzRoleEntity admin = newRole("res-admin", Set.of("system.admin"));
        grant(SubjectType.USER, user.getId().toString(), viewer, "project", "42");
        grant(SubjectType.USER, user.getId().toString(), admin, "", "");

        var resolved = new JpaGrantResolver(grants)
                .resolveGrants(Subject.user(user.getId().toString()));

        assertThat(resolved).hasSize(2);
        assertThat(resolved).anySatisfy(g -> {
            assertThat(g.scope()).isEqualTo(Scope.of("project", "42"));
            assertThat(g.permissions()).containsExactly("issue.read");
        });
        assertThat(resolved).anySatisfy(g -> {
            assertThat(g.scope()).isEqualTo(Scope.GLOBAL);
            assertThat(g.permissions()).containsExactly("system.admin");
        });
    }

    @Test
    void resolvesGroupMemberships() {
        AuthzUserEntity user = newUser("mem-user");
        AuthzGroupEntity group = new AuthzGroupEntity();
        group.setName("mem-group");
        groups.save(group);
        AuthzGroupMemberEntity member = new AuthzGroupMemberEntity();
        member.setGroupId(group.getId());
        member.setUserId(user.getId());
        members.save(member);

        var resolver = new JpaGroupMembershipResolver(members);
        assertThat(resolver.groupsOf(user.getId().toString()))
                .containsExactly(group.getId().toString());
        assertThat(resolver.groupsOf("not-a-uuid")).isEmpty();
    }
}
