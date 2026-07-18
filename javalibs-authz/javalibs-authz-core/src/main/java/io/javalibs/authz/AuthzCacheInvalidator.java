package io.javalibs.authz;

/**
 * Eviction hook for authorization caches. AuthzManagementService calls this after every
 * grant / membership mutation so permission changes take effect immediately on the local
 * instance; across instances the cache TTL is the safety net.
 */
public interface AuthzCacheInvalidator {

    /**
     * Evicts every cached grant of the subject.
     *
     * @param subject the subject whose grants should be evicted from cache
     */
    void evictSubject(Subject subject);

    /**
     * Evicts the user's cached group memberships and user-level grants.
     *
     * @param userId the user ID whose cache entries should be evicted
     */
    void evictUser(String userId);

    /**
     * Clears all authorization caches.
     */
    void evictAll();
}
