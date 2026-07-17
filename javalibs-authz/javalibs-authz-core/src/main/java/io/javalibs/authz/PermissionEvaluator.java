package io.javalibs.authz;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates effective permissions, YouTrack-style and purely additive (no deny rules):
 * a user has permission P in scope S when any grant of the user itself or of a group the
 * user belongs to carries P and is either global or exactly scoped to S.
 */
public final class PermissionEvaluator {

    private final GrantResolver grantResolver;
    private final GroupMembershipResolver groupMembershipResolver;

    public PermissionEvaluator(GrantResolver grantResolver,
            GroupMembershipResolver groupMembershipResolver) {
        this.grantResolver = Objects.requireNonNull(grantResolver, "grantResolver must not be null");
        this.groupMembershipResolver = Objects.requireNonNull(
                groupMembershipResolver, "groupMembershipResolver must not be null");
    }

    /** Returns whether the user holds the permission in the given scope. */
    public boolean hasPermission(String userId, String permission, Scope scope) {
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        for (Subject subject : subjectsOf(userId)) {
            for (ResolvedGrant grant : grantResolver.resolveGrants(subject)) {
                if (grant.appliesTo(scope) && grant.permissions().contains(permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns every permission the user holds in the given scope. */
    public Set<String> effectivePermissions(String userId, Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        Set<String> permissions = new LinkedHashSet<>();
        for (Subject subject : subjectsOf(userId)) {
            for (ResolvedGrant grant : grantResolver.resolveGrants(subject)) {
                if (grant.appliesTo(scope)) {
                    permissions.addAll(grant.permissions());
                }
            }
        }
        return permissions;
    }

    private Set<Subject> subjectsOf(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Set<Subject> subjects = new LinkedHashSet<>();
        subjects.add(Subject.user(userId));
        for (String groupId : groupMembershipResolver.groupsOf(userId)) {
            subjects.add(Subject.group(groupId));
        }
        return subjects;
    }
}
