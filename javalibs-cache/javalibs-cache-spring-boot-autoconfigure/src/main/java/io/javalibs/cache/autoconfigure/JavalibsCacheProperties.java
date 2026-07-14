package io.javalibs.cache.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for the javalibs cache module
 * ({@code javalibs.cache.*}).
 */
@ConfigurationProperties(prefix = "javalibs.cache")
public class JavalibsCacheProperties {

    /** Cache provider selection strategy. */
    public enum Provider {
        /** Redis when a RedisConnectionFactory bean exists, otherwise Caffeine. */
        AUTO,
        /** Redis only; no CacheManager is created when Redis is unavailable. */
        REDIS,
        /** In-memory Caffeine, even when Redis is on the classpath. */
        CAFFEINE
    }

    /** Master switch for the javalibs cache auto-configuration. */
    private boolean enabled = true;

    /** Provider selection; AUTO prefers Redis when available. */
    private Provider provider = Provider.AUTO;

    /**
     * Application-level key prefix; defaults to spring.application.name. Full keys
     * follow {@code <key-prefix>::<cacheName>::<key>}.
     */
    private String keyPrefix;

    /** Default TTL applied to caches without an explicit entry in {@link #caches}. */
    private Duration defaultTtl = Duration.ofMinutes(10);

    /** Whether null values are cached (usually left off to avoid caching misses). */
    private boolean cacheNullValues = false;

    /** Per-cache tuning, keyed by cache name. */
    private Map<String, Spec> caches = new LinkedHashMap<>();

    /** Per-cache tuning values. */
    public static class Spec {

        /** Entry time-to-live for this cache; falls back to javalibs.cache.default-ttl. */
        private Duration ttl;

        /** Maximum entries (honored by Caffeine only). */
        private Long maxSize;

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(Long maxSize) {
            this.maxSize = maxSize;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public boolean isCacheNullValues() {
        return cacheNullValues;
    }

    public void setCacheNullValues(boolean cacheNullValues) {
        this.cacheNullValues = cacheNullValues;
    }

    public Map<String, Spec> getCaches() {
        return caches;
    }

    public void setCaches(Map<String, Spec> caches) {
        this.caches = caches;
    }
}
