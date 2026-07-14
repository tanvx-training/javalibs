package io.javalibs.cache.autoconfigure;

import io.javalibs.cache.spring.RedisCacheConfigurations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheAspectSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates a {@link RedisCacheManager} with the javalibs conventions (JSON values,
 * {@code <prefix>::<cacheName>::} keys, per-cache TTLs) when Redis is available.
 *
 * <p>Runs before Spring Boot's {@link CacheAutoConfiguration} and, like it, only
 * activates when the application declared {@code @EnableCaching}.
 */
@AutoConfiguration(before = CacheAutoConfiguration.class)
@ConditionalOnClass({RedisConnectionFactory.class, RedisCacheManager.class})
@ConditionalOnBean(CacheAspectSupport.class)
@ConditionalOnProperty(prefix = "javalibs.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(ProviderConditions.RedisAllowed.class)
@EnableConfigurationProperties(JavalibsCacheProperties.class)
public class JavalibsRedisCacheAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(CacheManager.class)
    public RedisCacheManager javalibsRedisCacheManager(RedisConnectionFactory connectionFactory,
                                                       JavalibsCacheProperties properties,
                                                       Environment environment) {
        String prefix = keyPrefix(properties, environment);
        RedisCacheConfiguration defaults = RedisCacheConfigurations.standard(
                prefix, properties.getDefaultTtl(), properties.isCacheNullValues());

        Map<String, RedisCacheConfiguration> perCache = new LinkedHashMap<>();
        properties.getCaches().forEach((name, spec) -> {
            Duration ttl = spec.getTtl() != null ? spec.getTtl() : properties.getDefaultTtl();
            perCache.put(name, RedisCacheConfigurations.standard(
                    prefix, ttl, properties.isCacheNullValues()));
        });

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    static String keyPrefix(JavalibsCacheProperties properties, Environment environment) {
        if (properties.getKeyPrefix() != null && !properties.getKeyPrefix().isBlank()) {
            return properties.getKeyPrefix();
        }
        return environment.getProperty("spring.application.name", "app");
    }
}
