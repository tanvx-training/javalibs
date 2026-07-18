package io.javalibs.authz.jpa;

import io.javalibs.authz.PermissionCatalog;
import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.authz.UnknownPermissionException;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AuthzManagementServiceIT extends BaseIntegrationTest {

    @Autowired AuthzUserRepository users;
    @Autowired AuthzGroupRepository groups;
    @Autowired AuthzGroupMemberRepository members;
    @Autowired AuthzRoleRepository roles;
    @Autowired AuthzRoleGrantRepository grants;

    AuthzManagementService service;
    PermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        service = new AuthzManagementService(users, groups, members, roles, grants,
                new PermissionCatalog(Set.of("issue.read", "issue.update")), null);
        evaluator = new PermissionEvaluator(
                new JpaGrantResolver(grants), new JpaGroupMembershipResolver(members));
    }

    @Test
    void endToEndGrantFlow() {
        UUID userId = service.createUser("mgmt-alice", "a@x.io", "Alice", "{bcrypt}x", true);
        UUID groupId = service.createGroup("mgmt-devs", "Developers");
        service.addUserToGroup(userId, groupId);
        service.createRole("mgmt-editor", "Editor", null, Set.of("issue.update"));
        service.grantRole(Subject.group(groupId.toString()), "mgmt-editor",
                Scope.of("project", "42"));

        assertThat(evaluator.hasPermission(userId.toString(), "issue.update",
                Scope.of("project", "42"))).isTrue();
        assertThat(evaluator.hasPermission(userId.toString(), "issue.update",
                Scope.of("project", "43"))).isFalse();

        service.revokeGrant(Subject.group(groupId.toString()), "mgmt-editor",
                Scope.of("project", "42"));
        assertThat(evaluator.hasPermission(userId.toString(), "issue.update",
                Scope.of("project", "42"))).isFalse();
    }

    @Test
    void grantRoleIsIdempotent() {
        UUID userId = service.createUser("mgmt-bob", null, null, "{bcrypt}x", true);
        service.createRole("mgmt-viewer", "Viewer", null, Set.of("issue.read"));
        service.grantRole(Subject.user(userId.toString()), "mgmt-viewer", Scope.GLOBAL);
        service.grantRole(Subject.user(userId.toString()), "mgmt-viewer", Scope.GLOBAL);
        assertThat(grants.findWithRoleBySubject(io.javalibs.authz.SubjectType.USER,
                userId.toString())).hasSize(1);
    }

    @Test
    void createRoleRejectsUnknownPermission() {
        assertThatExceptionOfType(UnknownPermissionException.class)
                .isThrownBy(() -> service.createRole("mgmt-bad", "Bad", null,
                        Set.of("bogus.perm")));
    }
}
