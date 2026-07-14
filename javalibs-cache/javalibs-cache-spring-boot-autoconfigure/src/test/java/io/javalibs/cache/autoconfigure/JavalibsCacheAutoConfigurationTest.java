package io.javalibs.cache.autoconfigure;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheAspectSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JavalibsRedisCacheAutoConfiguration.class,
                    JavalibsCaffeineCacheAutoConfiguration.class));

    @Configuration
    @EnableCaching
    static class CachingEnabled {
    }

    @Configuration
    static class WithRedisFactory {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(RedisConnectionFactory.class);
        }
    }

    @Configuration
    static class WithUserCacheManager {
        @Bean
        CacheManager userCacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Test
    void caffeineByDefaultWithPerCacheSpecs() {
        runner.withUserConfiguration(CachingEnabled.class)
                .withPropertyValues(
                        "javalibs.cache.caches.orders.ttl=30s",
                        "javalibs.cache.caches.orders.max-size=500")
                .run(context -> {
                    assertThat(context).hasSingleBean(CaffeineCacheManager.class);
                    CaffeineCache orders = (CaffeineCache) context.getBean(CacheManager.class).getCache("orders");
                    Cache<Object, Object> nativeCache = orders.getNativeCache();
                    assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                            .isEqualTo(Duration.ofSeconds(30));
                    assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(500);
                });
    }

    @Test
    void redisWinsInAutoModeWhenFactoryPresent() {
        runner.withUserConfiguration(CachingEnabled.class, WithRedisFactory.class)
                .withPropertyValues(
                        "spring.application.name=orders-service",
                        "javalibs.cache.caches.orders.ttl=30s")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisCacheManager.class);
                    assertThat(context).doesNotHaveBean(CaffeineCacheManager.class);
                    RedisCache orders = (RedisCache) context.getBean(CacheManager.class).getCache("orders");
                    assertThat(orders.getCacheConfiguration().getTtlFunction()
                            .getTimeToLive(Object.class, null)).isEqualTo(Duration.ofSeconds(30));
                    assertThat(orders.getCacheConfiguration().getKeyPrefixFor("orders"))
                            .isEqualTo("orders-service::orders::");
                });
    }

    @Test
    void caffeineForcedEvenWithRedisPresent() {
        runner.withUserConfiguration(CachingEnabled.class, WithRedisFactory.class)
                .withPropertyValues("javalibs.cache.provider=caffeine")
                .run(context -> assertThat(context).hasSingleBean(CaffeineCacheManager.class));
    }

    @Test
    void redisForcedWithoutFactoryCreatesNothing() {
        // No CacheManager is produced; @EnableCaching then fails the context at
        // refresh time, which is Spring's own guard — assert exactly that.
        runner.withUserConfiguration(CachingEnabled.class)
                .withPropertyValues("javalibs.cache.provider=redis")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void inactiveWithoutEnableCaching() {
        runner.run(context -> assertThat(context).doesNotHaveBean(CacheManager.class));
    }

    @Test
    void disabledFlagProducesNoManager() {
        runner.withUserConfiguration(CachingEnabled.class)
                .withPropertyValues("javalibs.cache.enabled=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void backsOffForUserCacheManager() {
        runner.withUserConfiguration(CachingEnabled.class, WithUserCacheManager.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class))
                            .isInstanceOf(ConcurrentMapCacheManager.class);
                });
    }

    @Test
    void cacheAspectSupportBeanIsPresentWithEnableCaching() {
        runner.withUserConfiguration(CachingEnabled.class)
                .run(context -> assertThat(context).hasSingleBean(CacheAspectSupport.class));
    }
}
