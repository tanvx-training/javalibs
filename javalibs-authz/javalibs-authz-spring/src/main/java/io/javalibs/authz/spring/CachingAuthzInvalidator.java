package io.javalibs.authz.spring;

import io.javalibs.authz.AuthzCacheInvalidator;
import io.javalibs.authz.Subject;
import io.javalibs.authz.SubjectType;

import java.util.Objects;

/**
 * {@link AuthzCacheInvalidator} evicting both Caffeine-backed resolver caches.
 */
public final class CachingAuthzInvalidator implements AuthzCacheInvalidator {

    private final CachingGrantResolver grantResolver;
    private final CachingGroupMembershipResolver groupMembershipResolver;

    /**
     * Creates a new caching authorization cache invalidator.
     *
     * @param grantResolver             the caching grant resolver whose cache should be invalidated;
     *                                  must not be null
     * @param groupMembershipResolver   the caching group membership resolver whose cache should be
     *                                  invalidated; must not be null
     * @throws NullPointerException if either resolver is null
     */
    public CachingAuthzInvalidator(CachingGrantResolver grantResolver,
            CachingGroupMembershipResolver groupMembershipResolver) {
        this.grantResolver = Objects.requireNonNull(grantResolver);
        this.groupMembershipResolver = Objects.requireNonNull(groupMembershipResolver);
    }

    /**
     * Evicts cached grants and memberships for the given subject.
     * For user subjects, evicts both grants and group memberships; for other subject types,
     * evicts only grants.
     *
     * @param subject the subject whose cache entries should be evicted; must not be null
     */
    @Override
    public void evictSubject(Subject subject) {
        grantResolver.evictSubject(subject);
        if (subject.type() == SubjectType.USER) {
            groupMembershipResolver.evictUser(subject.id());
        }
    }

    /**
     * Evicts the user's cached group memberships and user-level grants.
     *
     * @param userId the user ID whose cache entries should be evicted; must not be null
     */
    @Override
    public void evictUser(String userId) {
        grantResolver.evictSubject(Subject.user(userId));
        groupMembershipResolver.evictUser(userId);
    }

    /**
     * Clears all authorization caches.
     */
    @Override
    public void evictAll() {
        grantResolver.evictAll();
        groupMembershipResolver.evictAll();
    }
}
