package io.javalibs.cache.spring;

import io.javalibs.cache.CacheSpec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CacheFactoriesTest {

    record CachedOrder(String id, Instant createdAt) {
    }

    @Test
    void redisConfigurationCarriesConventionAndTtl() {
        var config = RedisCacheConfigurations.standard("orders-service", Duration.ofMinutes(10), false);

        assertThat(config.getTtlFunction().getTimeToLive(Object.class, null))
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(config.getKeyPrefixFor("orders")).isEqualTo("orders-service::orders::");
        assertThat(config.getAllowCacheNullValues()).isFalse();
        assertThat(RedisCacheConfigurations.standard("x", Duration.ofSeconds(1), true)
                .getAllowCacheNullValues()).isTrue();
    }

    @Test
    void jsonSerializerRoundTripsRecordsWithJavaTime() {
        var serializer = RedisCacheConfigurations.jsonSerializer();
        var order = new CachedOrder("o-1", Instant.parse("2026-07-13T10:00:00Z"));

        byte[] bytes = serializer.serialize(order);
        Object back = serializer.deserialize(bytes);

        assertThat(back).isEqualTo(order);
    }

    @Test
    void caffeineBuilderAppliesSpec() {
        var cache = CaffeineCaches.builder(new CacheSpec(Duration.ofSeconds(30), 500L), Duration.ofMinutes(10))
                .build();
        assertThat(cache.policy().expireAfterWrite()).isPresent();
        assertThat(cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(500L);

        var fallback = CaffeineCaches.builder(null, Duration.ofMinutes(10)).build();
        assertThat(fallback.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(Duration.ofMinutes(10));
    }
}
