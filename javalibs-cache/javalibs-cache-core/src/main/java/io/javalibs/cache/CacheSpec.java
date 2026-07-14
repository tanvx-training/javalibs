package io.javalibs.cache;

import java.time.Duration;

/**
 * Per-cache tuning values, independent of the cache provider.
 *
 * @param ttl     time-to-live for entries; {@code null} means "use the default TTL"
 * @param maxSize maximum number of entries (honored by in-memory providers such
 *                as Caffeine); {@code null} means unbounded
 */
public record CacheSpec(Duration ttl, Long maxSize) {

    public CacheSpec {
        if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
            throw new IllegalArgumentException("ttl must be positive, got " + ttl);
        }
        if (maxSize != null && maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got " + maxSize);
        }
    }

    /** Spec with only a TTL. */
    public static CacheSpec ofTtl(Duration ttl) {
        return new CacheSpec(ttl, null);
    }
}
