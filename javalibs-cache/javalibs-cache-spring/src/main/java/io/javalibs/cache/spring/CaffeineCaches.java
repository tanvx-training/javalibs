package io.javalibs.cache.spring;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.javalibs.cache.CacheSpec;

import java.time.Duration;

/**
 * Builds Caffeine cache builders from provider-independent {@link CacheSpec}s.
 */
public final class CaffeineCaches {

    private CaffeineCaches() {
    }

    /**
     * Creates a Caffeine builder applying the spec's TTL (expire-after-write) and
     * maximum size when present, falling back to {@code defaultTtl} when the spec
     * has no TTL of its own.
     */
    public static Caffeine<Object, Object> builder(CacheSpec spec, Duration defaultTtl) {
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        Duration ttl = spec != null && spec.ttl() != null ? spec.ttl() : defaultTtl;
        if (ttl != null) {
            caffeine.expireAfterWrite(ttl);
        }
        if (spec != null && spec.maxSize() != null) {
            caffeine.maximumSize(spec.maxSize());
        }
        return caffeine;
    }
}
