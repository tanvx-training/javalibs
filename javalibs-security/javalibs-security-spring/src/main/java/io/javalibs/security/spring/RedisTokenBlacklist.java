package io.javalibs.security.spring;

import io.javalibs.security.TokenBlacklist;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Redis-backed {@link TokenBlacklist} shared by all instances of a service —
 * the correct implementation for horizontally scaled microservices.
 *
 * <p>Each revocation is stored as {@code <keyPrefix><jti>} with a TTL equal to
 * the token's remaining lifetime, so Redis expires entries exactly when the
 * revoked token would have expired anyway; the list never grows beyond the set
 * of currently revoked, still-live tokens.</p>
 */
public class RedisTokenBlacklist implements TokenBlacklist {

    /** Default Redis key prefix for revocation entries. */
    public static final String DEFAULT_KEY_PREFIX = "javalibs:security:revoked:";

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisTokenBlacklist(StringRedisTemplate redis) {
        this(redis, DEFAULT_KEY_PREFIX);
    }

    public RedisTokenBlacklist(StringRedisTemplate redis, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
    }

    @Override
    public void revoke(String tokenId, Instant expiresAt) {
        Objects.requireNonNull(tokenId, "tokenId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redis.opsForValue().set(keyPrefix + tokenId, "1", ttl);
        }
    }

    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(keyPrefix + tokenId));
    }
}
