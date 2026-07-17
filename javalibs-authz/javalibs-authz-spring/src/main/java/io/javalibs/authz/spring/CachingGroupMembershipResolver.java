package io.javalibs.authz.spring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.javalibs.authz.GroupMembershipResolver;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Caffeine-backed decorator caching {@link GroupMembershipResolver#groupsOf(String)} per user
 * with a fixed TTL (expire-after-write).
 */
public final class CachingGroupMembershipResolver implements GroupMembershipResolver {

    private final GroupMembershipResolver delegate;
    private final Cache<String, Set<String>> cache;

    /**
     * Creates a new caching group membership resolver decorator.
     *
     * @param delegate the underlying group membership resolver to cache; must not be null
     * @param ttl     the time-to-live for cached entries; must not be null
     * @param maxSize the maximum number of cached entries
     * @throws NullPointerException if delegate or ttl is null
     */
    public CachingGroupMembershipResolver(GroupMembershipResolver delegate, Duration ttl,
            long maxSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
    }

    /**
     * Resolves group memberships for the given user, using cache if available.
     *
     * @param userId the user ID to resolve group memberships for; must not be null
     * @return a set of group IDs the user belongs to
     */
    @Override
    public Set<String> groupsOf(String userId) {
        return cache.get(userId, delegate::groupsOf);
    }

    /**
     * Evicts the cached memberships of the user.
     *
     * @param userId the user ID whose memberships should be evicted from cache; must not be null
     */
    public void evictUser(String userId) {
        cache.invalidate(userId);
    }

    /**
     * Clears all cached memberships.
     */
    public void evictAll() {
        cache.invalidateAll();
    }
}
