package io.javalibs.security.autoconfigure;

import io.javalibs.security.InMemoryTokenBlacklist;
import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.spring.RedisTokenBlacklist;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Registers a {@link TokenBlacklist} according to
 * {@code javalibs.security.blacklist.mode}:
 *
 * <ul>
 *   <li>{@code none} (default) — no revocation; tokens stay valid until expiry;</li>
 *   <li>{@code in-memory} — {@link InMemoryTokenBlacklist}; single instance only;</li>
 *   <li>{@code redis} — {@link RedisTokenBlacklist}; requires spring-data-redis and a
 *       {@link StringRedisTemplate} bean (Spring Boot provides one when
 *       {@code spring.data.redis.*} is configured).</li>
 * </ul>
 *
 * <p>Runs before {@link JavalibsSecurityAutoConfiguration} so the JWT filter can
 * pick the blacklist up.</p>
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        before = JavalibsSecurityAutoConfiguration.class)
@ConditionalOnProperty(prefix = "javalibs.security", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityBlacklistAutoConfiguration {

    /**
     * In-memory blacklist for single-instance deployments.
     *
     * @return the blacklist
     */
    @Bean
    @ConditionalOnMissingBean(TokenBlacklist.class)
    @ConditionalOnProperty(prefix = "javalibs.security.blacklist", name = "mode",
            havingValue = "in-memory")
    public InMemoryTokenBlacklist javalibsInMemoryTokenBlacklist() {
        return new InMemoryTokenBlacklist();
    }

    /**
     * Redis-backed blacklist shared across service instances.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    static class RedisBlacklistConfiguration {

        @Bean
        @ConditionalOnMissingBean(TokenBlacklist.class)
        @ConditionalOnBean(StringRedisTemplate.class)
        @ConditionalOnProperty(prefix = "javalibs.security.blacklist", name = "mode",
                havingValue = "redis")
        RedisTokenBlacklist javalibsRedisTokenBlacklist(StringRedisTemplate redisTemplate,
                SecurityProperties properties) {
            return new RedisTokenBlacklist(redisTemplate, properties.getBlacklist().getKeyPrefix());
        }
    }
}
