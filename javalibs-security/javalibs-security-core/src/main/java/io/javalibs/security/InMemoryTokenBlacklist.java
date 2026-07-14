package io.javalibs.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link TokenBlacklist} for single-instance deployments and tests.
 *
 * <p><strong>Not suitable for horizontally scaled services:</strong> each
 * instance would hold its own list, so a token revoked on one instance stays
 * valid on the others. Use the Redis-backed implementation
 * ({@code javalibs.security.blacklist.mode=redis}) in microservice setups.</p>
 *
 * <p>Expired entries are purged lazily on every write.</p>
 */
public class InMemoryTokenBlacklist implements TokenBlacklist {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryTokenBlacklist() {
        this(Clock.systemUTC());
    }

    /** Visible for tests: allows a fixed clock. */
    public InMemoryTokenBlacklist(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void revoke(String tokenId, Instant expiresAt) {
        Objects.requireNonNull(tokenId, "tokenId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        purgeExpired();
        if (expiresAt.isAfter(clock.instant())) {
            revoked.put(tokenId, expiresAt);
        }
    }

    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        Instant expiresAt = revoked.get(tokenId);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(clock.instant())) {
            revoked.remove(tokenId);
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        revoked.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
