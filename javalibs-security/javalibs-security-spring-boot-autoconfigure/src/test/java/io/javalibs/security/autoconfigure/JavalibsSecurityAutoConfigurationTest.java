package io.javalibs.security.autoconfigure;

import io.javalibs.security.TokenValidator;
import io.javalibs.security.UserContext;
import io.javalibs.security.spring.JwtAuthenticationFilter;
import io.javalibs.security.spring.RestAccessDeniedHandler;
import io.javalibs.security.spring.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JavalibsSecurityAutoConfiguration} and
 * {@link SecurityWebMvcAutoConfiguration} using {@link WebApplicationContextRunner}.
 */
class JavalibsSecurityAutoConfigurationTest {

    private static final String SECRET_PROPERTY =
            "javalibs.security.jwt.secret=0123456789abcdef0123456789abcdef";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JavalibsSecurityAutoConfiguration.class,
                    SecurityWebMvcAutoConfiguration.class,
                    SecurityAutoConfiguration.class));

    @Test
    void configuredSecretRegistersAllBeans() {
        contextRunner
                .withPropertyValues(SECRET_PROPERTY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TokenValidator.class);
                    assertThat(context).hasSingleBean(JwtAuthenticationFilter.class);
                    assertThat(context).hasSingleBean(RestAuthenticationEntryPoint.class);
                    assertThat(context).hasSingleBean(RestAccessDeniedHandler.class);
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context).hasBean("javalibsSecurityFilterChain");
                    assertThat(context).hasBean("javalibsSecurityWebMvcConfigurer");
                });
    }

    @Test
    void disabledFlagBacksOffCompletely() {
        contextRunner
                .withPropertyValues(SECRET_PROPERTY, "javalibs.security.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TokenValidator.class);
                    assertThat(context).doesNotHaveBean(JwtAuthenticationFilter.class);
                    assertThat(context).doesNotHaveBean(RestAuthenticationEntryPoint.class);
                    assertThat(context).doesNotHaveBean(RestAccessDeniedHandler.class);
                    assertThat(context).doesNotHaveBean("javalibsSecurityFilterChain");
                    assertThat(context).doesNotHaveBean("javalibsSecurityWebMvcConfigurer");
                });
    }

    @Test
    void userProvidedSecurityFilterChainMakesAutoConfigurationBackOff() {
        contextRunner
                .withPropertyValues(SECRET_PROPERTY)
                .withUserConfiguration(CustomFilterChainConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context).hasBean("customFilterChain");
                    assertThat(context).doesNotHaveBean("javalibsSecurityFilterChain");
                    // the supporting beans remain available for the custom chain
                    assertThat(context).hasSingleBean(TokenValidator.class);
                });
    }

    @Test
    void userProvidedTokenValidatorWins() {
        contextRunner
                .withUserConfiguration(CustomTokenValidatorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TokenValidator.class);
                    assertThat(context.getBean(TokenValidator.class))
                            .isSameAs(context.getBean(CustomTokenValidatorConfiguration.class).validator);
                });
    }

    @Test
    void missingKeyMaterialFailsFastWithActionableMessage() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("javalibs.security.jwt.secret")
                    .hasMessageContaining("javalibs.security.jwt.public-key");
        });
    }

    @Test
    void propertiesAreBound() {
        contextRunner
                .withPropertyValues(SECRET_PROPERTY,
                        "javalibs.security.permit-all=/public/**,/docs/**",
                        "javalibs.security.jwt.issuer=https://sso.example.com",
                        "javalibs.security.jwt.clock-skew=90s",
                        "javalibs.security.jwt.roles-claim=authorities")
                .run(context -> {
                    SecurityProperties properties = context.getBean(SecurityProperties.class);
                    assertThat(properties.getPermitAll()).containsExactly("/public/**", "/docs/**");
                    assertThat(properties.getJwt().getIssuer()).isEqualTo("https://sso.example.com");
                    assertThat(properties.getJwt().getClockSkew().toSeconds()).isEqualTo(90);
                    assertThat(properties.getJwt().getRolesClaim()).isEqualTo("authorities");
                });
    }

    @Test
    void webMvcConfigurerIsRegisteredIndependentlyOfKeyMaterial() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityWebMvcAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WebMvcConfigurer.class);
                });
    }

    /** User configuration providing its own {@link SecurityFilterChain}. */
    @Configuration(proxyBeanMethods = false)
    static class CustomFilterChainConfiguration {

        @Bean
        SecurityFilterChain customFilterChain() {
            return new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE);
        }
    }

    /** User configuration providing its own {@link TokenValidator}. */
    @Configuration(proxyBeanMethods = false)
    static class CustomTokenValidatorConfiguration {

        final TokenValidator validator = token -> UserContext.builder().userId("custom").build();

        @Bean
        TokenValidator customTokenValidator() {
            return validator;
        }
    }
}
