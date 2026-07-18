package io.javalibs.authz.spring;

import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.Scope;
import io.javalibs.security.UserContext;
import io.javalibs.security.spring.UserContextHolder;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;
import java.util.Optional;

/**
 * Programmatic permission checks for services and controllers.
 *
 * The no-{@code userId} overloads resolve the current caller from
 * {@link UserContextHolder}. {@code check} returns {@code false} for anonymous
 * callers while {@code require} throws {@link AccessDeniedException} (translated
 * to 403/401 by the security layer).
 */
public class PermissionChecker {

    private final PermissionEvaluator evaluator;

    /**
     * Creates a new permission checker with the given evaluator.
     *
     * @param evaluator the permission evaluator, must not be null
     * @throws NullPointerException if evaluator is null
     */
    public PermissionChecker(PermissionEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    }

    /**
     * Returns whether the current caller holds the given permission in the scope.
     *
     * @param permission the permission code to check
     * @param scope the scope within which to check the permission
     * @return {@code true} if the current authenticated caller holds the
     *         permission, {@code false} if unauthenticated or lacks permission
     */
    public boolean check(String permission, Scope scope) {
        Optional<UserContext> user = UserContextHolder.current();
        return user.isPresent() && check(user.get().userId(), permission, scope);
    }

    /**
     * Returns whether the given user holds the permission in the scope.
     *
     * @param userId the user ID to check
     * @param permission the permission code to check
     * @param scope the scope within which to check the permission
     * @return {@code true} if the user holds the permission, {@code false}
     *         otherwise
     */
    public boolean check(String userId, String permission, Scope scope) {
        return evaluator.hasPermission(userId, permission, scope);
    }

    /**
     * Asserts the current caller holds the given permission in the scope.
     *
     * @param permission the permission code to check
     * @param scope the scope within which to check the permission
     * @throws AccessDeniedException if the caller is unauthenticated or lacks
     *         the permission
     */
    public void require(String permission, Scope scope) {
        UserContext user = UserContextHolder.current().orElseThrow(
                () -> new AccessDeniedException(
                        "Access denied: authentication is required to access this resource"));
        require(user.userId(), permission, scope);
    }

    /**
     * Asserts the given user holds the permission in the scope.
     *
     * @param userId the user ID to check
     * @param permission the permission code to check
     * @param scope the scope within which to check the permission
     * @throws AccessDeniedException if the user lacks the permission
     */
    public void require(String userId, String permission, Scope scope) {
        if (!check(userId, permission, scope)) {
            throw new AccessDeniedException("Access denied: caller does not have permission '"
                    + permission + "' in scope " + describe(scope));
        }
    }

    private static String describe(Scope scope) {
        return scope.isGlobal() ? "GLOBAL" : scope.type() + ":" + scope.id();
    }
}
