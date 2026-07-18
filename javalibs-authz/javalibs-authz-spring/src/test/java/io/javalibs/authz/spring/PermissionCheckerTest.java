package io.javalibs.authz.spring;

import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PermissionCheckerTest {

    private static final Scope PROJECT_42 = Scope.of("project", "42");

    private final PermissionChecker checker = new PermissionChecker(new PermissionEvaluator(
            subject -> subject.equals(Subject.user("u1"))
                    ? List.of(new ResolvedGrant(PROJECT_42, Set.of("issue.read")))
                    : List.of(),
            userId -> Set.of()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        UserContext user = UserContext.builder().userId(userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new io.javalibs.security.spring.UserContextAuthenticationToken(user));
    }

    @Test
    void checksExplicitUserId() {
        assertThat(checker.check("u1", "issue.read", PROJECT_42)).isTrue();
        assertThat(checker.check("u2", "issue.read", PROJECT_42)).isFalse();
    }

    @Test
    void usesCurrentUserWhenNoUserIdGiven() {
        authenticateAs("u1");
        assertThat(checker.check("issue.read", PROJECT_42)).isTrue();
        checker.require("issue.read", PROJECT_42);
    }

    @Test
    void checkIsFalseAndRequireThrowsWhenUnauthenticated() {
        assertThat(checker.check("issue.read", PROJECT_42)).isFalse();
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> checker.require("issue.read", PROJECT_42));
    }

    @Test
    void requireThrowsWhenPermissionMissing() {
        authenticateAs("u1");
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> checker.require("issue.delete", PROJECT_42))
                .withMessageContaining("issue.delete");
    }
}
