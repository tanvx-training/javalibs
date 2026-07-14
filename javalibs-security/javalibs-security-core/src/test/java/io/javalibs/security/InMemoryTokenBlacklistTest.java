package io.javalibs.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTokenBlacklistTest {

    private final Instant now = Instant.parse("2026-07-13T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void revokedTokenIsRejectedUntilExpiry() {
        var blacklist = new InMemoryTokenBlacklist(fixedClock);
        blacklist.revoke("jti-1", now.plus(Duration.ofHours(1)));

        assertThat(blacklist.isRevoked("jti-1")).isTrue();
        assertThat(blacklist.isRevoked("jti-other")).isFalse();
        assertThat(blacklist.isRevoked(null)).isFalse();
    }

    @Test
    void expiredEntriesAreIgnoredAndPurged() {
        var mutableNow = new Instant[]{now};
        Clock movingClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return mutableNow[0];
            }
        };
        var blacklist = new InMemoryTokenBlacklist(movingClock);
        blacklist.revoke("jti-1", now.plus(Duration.ofMinutes(5)));
        assertThat(blacklist.isRevoked("jti-1")).isTrue();

        mutableNow[0] = now.plus(Duration.ofMinutes(6));
        assertThat(blacklist.isRevoked("jti-1")).isFalse();
    }

    @Test
    void revokingAlreadyExpiredTokenIsNoOp() {
        var blacklist = new InMemoryTokenBlacklist(fixedClock);
        blacklist.revoke("jti-1", now.minusSeconds(1));
        assertThat(blacklist.isRevoked("jti-1")).isFalse();
    }
}
