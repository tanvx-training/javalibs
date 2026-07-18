package io.javalibs.authz.spring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.javalibs.authz.GrantResolver;
import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Subject;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Caffeine-backed decorator caching {@link GrantResolver#resolveGrants(Subject)} per subject
 * with a fixed TTL (expire-after-write).
 */
public final class CachingGrantResolver implements GrantResolver {

    private final GrantResolver delegate;
    private final Cache<Subject, List<ResolvedGrant>> cache;

    /**
     * Creates a new caching grant resolver decorator.
     *
     * @param delegate the underlying grant resolver to cache; must not be null
     * @param ttl     the time-to-live for cached entries; must not be null
     * @param maxSize the maximum number of cached entries
     * @throws NullPointerException if delegate or ttl is null
     */
    public CachingGrantResolver(GrantResolver delegate, Duration ttl, long maxSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
    }

    /**
     * Resolves grants for the given subject, using cache if available.
     *
     * @param subject the subject to resolve grants for; must not be null
     * @return a list of resolved grants for the subject
     */
    @Override
    public List<ResolvedGrant> resolveGrants(Subject subject) {
        return cache.get(subject, delegate::resolveGrants);
    }

    /**
     * Evicts the cached grants of the subject.
     *
     * @param subject the subject whose grants should be evicted from cache; must not be null
     */
    public void evictSubject(Subject subject) {
        cache.invalidate(subject);
    }

    /**
     * Clears all cached grants.
     */
    public void evictAll() {
        cache.invalidateAll();
    }
}
