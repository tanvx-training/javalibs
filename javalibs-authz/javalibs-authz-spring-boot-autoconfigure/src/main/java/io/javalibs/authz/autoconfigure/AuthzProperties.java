package io.javalibs.authz.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the javalibs authz module, bound from the
 * {@code javalibs.authz.*} namespace.
 *
 * <pre>{@code
 * javalibs:
 *   authz:
 *     enabled: true
 *     cache:
 *       enabled: true
 *       ttl: 60s
 *       max-size: 10000
 *     jpa:
 *       enabled: true
 *       apply-migrations: true
 * }</pre>
 */
@ConfigurationProperties("javalibs.authz")
public class AuthzProperties {

    /** Whether the authz auto-configuration is active. */
    private boolean enabled = true;

    /** Resolver caching settings ({@code javalibs.authz.cache.*}). */
    private final Cache cache = new Cache();

    /** Default JPA persistence settings ({@code javalibs.authz.jpa.*}). */
    private final Jpa jpa = new Jpa();

    /**
     * Returns whether the authz auto-configuration is enabled.
     *
     * @return {@code true} when enabled (the default)
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the authz auto-configuration.
     *
     * @param enabled the new value
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the resolver caching settings.
     *
     * @return the nested cache properties
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * Returns the default JPA persistence settings.
     *
     * @return the nested JPA properties
     */
    public Jpa getJpa() {
        return jpa;
    }

    /** Resolver caching settings ({@code javalibs.authz.cache.*}). */
    public static class Cache {

        /** Whether resolver results are cached (requires Caffeine on the classpath). */
        private boolean enabled = true;

        /** Time-to-live of cached grants/memberships; the upper bound for stale permissions across instances. */
        private Duration ttl = Duration.ofSeconds(60);

        /** Maximum number of subjects kept per cache. */
        private long maxSize = 10_000;

        /**
         * Returns whether resolver caching is enabled.
         *
         * @return {@code true} when enabled (the default)
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables resolver caching.
         *
         * @param enabled the new value
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the time-to-live of cached entries.
         *
         * @return the cache TTL
         */
        public Duration getTtl() {
            return ttl;
        }

        /**
         * Sets the time-to-live of cached entries.
         *
         * @param ttl the cache TTL
         */
        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        /**
         * Returns the maximum number of subjects kept per cache.
         *
         * @return the cache max size
         */
        public long getMaxSize() {
            return maxSize;
        }

        /**
         * Sets the maximum number of subjects kept per cache.
         *
         * @param maxSize the cache max size
         */
        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }
    }

    /** Default JPA persistence settings ({@code javalibs.authz.jpa.*}). */
    public static class Jpa {

        /** Whether the javalibs-authz-jpa beans are auto-configured when the module is present. */
        private boolean enabled = true;

        /** Whether the bundled Flyway migration location is appended automatically. */
        private boolean applyMigrations = true;

        /**
         * Returns whether the JPA wiring is enabled.
         *
         * @return {@code true} when enabled (the default)
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the JPA wiring.
         *
         * @param enabled the new value
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns whether the bundled Flyway migration location is appended automatically.
         *
         * @return {@code true} when the migration location is appended (the default)
         */
        public boolean isApplyMigrations() {
            return applyMigrations;
        }

        /**
         * Sets whether the bundled Flyway migration location is appended automatically.
         *
         * @param applyMigrations the new value
         */
        public void setApplyMigrations(boolean applyMigrations) {
            this.applyMigrations = applyMigrations;
        }
    }
}
