package io.javalibs.authz;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionEvaluatorTest {

    private static final Scope PROJECT_42 = Scope.of("project", "42");
    private static final Scope PROJECT_43 = Scope.of("project", "43");

    private PermissionEvaluator evaluator(Map<Subject, List<ResolvedGrant>> grants,
            Map<String, Set<String>> groups) {
        return new PermissionEvaluator(
                subject -> grants.getOrDefault(subject, List.of()),
                userId -> groups.getOrDefault(userId, Set.of()));
    }

    @Test
    void directScopedGrantMatchesOnlyItsScope() {
        PermissionEvaluator evaluator = evaluator(
                Map.of(Subject.user("u1"),
                        List.of(new ResolvedGrant(PROJECT_42, Set.of("issue.read")))),
                Map.of());
        assertThat(evaluator.hasPermission("u1", "issue.read", PROJECT_42)).isTrue();
        assertThat(evaluator.hasPermission("u1", "issue.read", PROJECT_43)).isFalse();
        assertThat(evaluator.hasPermission("u1", "issue.read", Scope.GLOBAL)).isFalse();
        assertThat(evaluator.hasPermission("u1", "issue.update", PROJECT_42)).isFalse();
    }

    @Test
    void globalGrantCoversEveryScope() {
        PermissionEvaluator evaluator = evaluator(
                Map.of(Subject.user("u1"),
                        List.of(new ResolvedGrant(Scope.GLOBAL, Set.of("issue.read")))),
                Map.of());
        assertThat(evaluator.hasPermission("u1", "issue.read", Scope.GLOBAL)).isTrue();
        assertThat(evaluator.hasPermission("u1", "issue.read", PROJECT_42)).isTrue();
        assertThat(evaluator.hasPermission("u1", "issue.read", PROJECT_43)).isTrue();
    }

    @Test
    void groupGrantsAreUnionedWithUserGrants() {
        PermissionEvaluator evaluator = evaluator(
                Map.of(
                        Subject.user("u1"),
                        List.of(new ResolvedGrant(PROJECT_42, Set.of("issue.read"))),
                        Subject.group("g1"),
                        List.of(new ResolvedGrant(PROJECT_42, Set.of("issue.update")))),
                Map.of("u1", Set.of("g1")));
        assertThat(evaluator.effectivePermissions("u1", PROJECT_42))
                .containsExactlyInAnyOrder("issue.read", "issue.update");
    }

    @Test
    void userWithoutGrantsHasNoPermissions() {
        PermissionEvaluator evaluator = evaluator(Map.of(), Map.of());
        assertThat(evaluator.hasPermission("nobody", "issue.read", Scope.GLOBAL)).isFalse();
        assertThat(evaluator.effectivePermissions("nobody", PROJECT_42)).isEmpty();
    }
}
