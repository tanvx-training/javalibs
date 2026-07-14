package io.javalibs.security.autoconfigure;

import io.javalibs.security.InMemoryTokenBlacklist;
import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.TokenValidator;
import io.javalibs.security.spring.RedisTokenBlacklist;
import io.javalibs.security.spring.UserContextJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEnhancementsAutoConfigurationTest {

    private static final String SECRET =
            "javalibs-test-secret-key-0123456789abcdef-0123456789";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityBlacklistAutoConfiguration.class,
                    JavalibsSecurityAutoConfiguration.class,
                    JavalibsOAuth2ResourceServerAutoConfiguration.class,
                    SecurityAutoConfiguration.class));

    @Configuration
    static class WithRedisTemplate {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }
    }

    @Configuration
    static class WithJwtDecoder {
        @Bean
        JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }
    }

    @Test
    void noBlacklistByDefault() {
        runner.withPropertyValues("javalibs.security.jwt.secret=" + SECRET)
                .run(context -> assertThat(context).doesNotHaveBean(TokenBlacklist.class));
    }

    @Test
    void inMemoryBlacklistMode() {
        runner.withPropertyValues(
                        "javalibs.security.jwt.secret=" + SECRET,
                        "javalibs.security.blacklist.mode=in-memory")
                .run(context -> {
                    assertThat(context).hasSingleBean(TokenBlacklist.class);
                    assertThat(context.getBean(TokenBlacklist.class))
                            .isInstanceOf(InMemoryTokenBlacklist.class);
                });
    }

    @Test
    void redisBlacklistModeRequiresTemplate() {
        runner.withUserConfiguration(WithRedisTemplate.class)
                .withPropertyValues(
                        "javalibs.security.jwt.secret=" + SECRET,
                        "javalibs.security.blacklist.mode=redis")
                .run(context -> assertThat(context.getBean(TokenBlacklist.class))
                        .isInstanceOf(RedisTokenBlacklist.class));

        runner.withPropertyValues(
                        "javalibs.security.jwt.secret=" + SECRET,
                        "javalibs.security.blacklist.mode=redis")
                .run(context -> assertThat(context).doesNotHaveBean(TokenBlacklist.class));
    }

    @Test
    void oauth2ModeReplacesJwtMode() {
        runner.withUserConfiguration(WithJwtDecoder.class)
                .withPropertyValues("javalibs.security.mode=oauth2-resource-server")
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context).hasSingleBean(UserContextJwtAuthenticationConverter.class);
                    // The self-validating jwt mode must be fully inactive.
                    assertThat(context).doesNotHaveBean(TokenValidator.class);
                });
    }

    @Test
    void oauth2ModeWithoutDecoderCreatesNoChainOfOurOwn() {
        runner.withPropertyValues("javalibs.security.mode=oauth2-resource-server")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("javalibsOAuth2SecurityFilterChain");
                    assertThat(context).doesNotHaveBean(TokenValidator.class);
                });
    }

    @Test
    void jwtModeIsTheDefault() {
        runner.withPropertyValues("javalibs.security.jwt.secret=" + SECRET)
                .run(context -> {
                    assertThat(context).hasSingleBean(TokenValidator.class);
                    assertThat(context).doesNotHaveBean(UserContextJwtAuthenticationConverter.class);
                });
    }
}
