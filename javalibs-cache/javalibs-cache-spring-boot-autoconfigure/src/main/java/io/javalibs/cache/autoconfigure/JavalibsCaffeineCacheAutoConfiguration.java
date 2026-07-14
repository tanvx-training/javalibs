package io.javalibs.cache.autoconfigure;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.javalibs.cache.CacheSpec;
import io.javalibs.cache.spring.CaffeineCaches;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheAspectSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Fallback in-memory {@link CaffeineCacheManager} applying the same per-cache
 * TTL/size tuning as the Redis variant. Chosen when Redis is unavailable (or
 * when {@code javalibs.cache.provider=caffeine} forces it).
 *
 * <p>Listed after {@link JavalibsRedisCacheAutoConfiguration} so Redis wins in
 * AUTO mode; the {@code @ConditionalOnMissingBean(CacheManager)} guard then
 * makes this configuration back off.
 */
@AutoConfiguration(before = CacheAutoConfiguration.class, after = JavalibsRedisCacheAutoConfiguration.class)
@ConditionalOnClass({Caffeine.class, CaffeineCacheManager.class})
@ConditionalOnBean(CacheAspectSupport.class)
@ConditionalOnProperty(prefix = "javalibs.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(ProviderConditions.CaffeineAllowed.class)
@EnableConfigurationProperties(JavalibsCacheProperties.class)
public class JavalibsCaffeineCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CaffeineCacheManager javalibsCaffeineCacheManager(JavalibsCacheProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // Default builder for caches created on demand.
        manager.setCaffeine(CaffeineCaches.builder(null, properties.getDefaultTtl()));
        properties.getCaches().forEach((name, spec) -> manager.registerCustomCache(name,
                CaffeineCaches.builder(new CacheSpec(spec.getTtl(), spec.getMaxSize()),
                        properties.getDefaultTtl()).build()));
        return manager;
    }
}
