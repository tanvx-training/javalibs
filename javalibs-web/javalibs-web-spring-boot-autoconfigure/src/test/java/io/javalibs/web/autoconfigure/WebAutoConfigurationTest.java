package io.javalibs.web.autoconfigure;

import io.javalibs.web.spring.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WebApplicationContextRunner} tests for the javalibs web auto-configurations.
 */
class WebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WebAutoConfiguration.class,
                    RequestLoggingAutoConfiguration.class,
                    CorsAutoConfiguration.class));

    @Test
    void defaultsRegisterExceptionHandlerAndLoggingFilterButNoCors() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasBean("javalibsRequestLoggingFilter");
            assertThat(context).doesNotHaveBean("javalibsCorsFilter");
            assertThat(context).hasSingleBean(WebProperties.class);
        });
    }

    @Test
    void defaultPropertiesAreBound() {
        runner.run(context -> {
            WebProperties properties = context.getBean(WebProperties.class);
            assertThat(properties.exceptionHandler().enabled()).isTrue();
            assertThat(properties.logging().enabled()).isTrue();
            assertThat(properties.logging().includePayload()).isFalse();
            assertThat(properties.logging().maxPayloadLength()).isEqualTo(2048);
            assertThat(properties.logging().excludedPaths()).containsExactly("/actuator/**");
            assertThat(properties.cors().enabled()).isFalse();
            assertThat(properties.cors().allowedMethods())
                    .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
            assertThat(properties.cors().allowedHeaders()).containsExactly("*");
            assertThat(properties.cors().allowCredentials()).isFalse();
            assertThat(properties.cors().maxAge()).isEqualTo(3600);
            assertThat(properties.cors().path()).isEqualTo("/**");
        });
    }

    @Test
    void corsFilterIsRegisteredWhenEnabled() {
        runner.withPropertyValues(
                        "javalibs.web.cors.enabled=true",
                        "javalibs.web.cors.allowed-origins=https://example.com")
                .run(context -> assertThat(context).hasBean("javalibsCorsFilter"));
    }

    @Test
    void exceptionHandlerCanBeDisabled() {
        runner.withPropertyValues("javalibs.web.exception-handler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Test
    void loggingFilterCanBeDisabled() {
        runner.withPropertyValues("javalibs.web.logging.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("javalibsRequestLoggingFilter"));
    }

    @Test
    void loggingPropertiesAreApplied() {
        runner.withPropertyValues(
                        "javalibs.web.logging.include-payload=true",
                        "javalibs.web.logging.max-payload-length=512",
                        "javalibs.web.logging.excluded-paths=/health,/metrics/**")
                .run(context -> {
                    WebProperties properties = context.getBean(WebProperties.class);
                    assertThat(properties.logging().includePayload()).isTrue();
                    assertThat(properties.logging().maxPayloadLength()).isEqualTo(512);
                    assertThat(properties.logging().excludedPaths())
                            .containsExactly("/health", "/metrics/**");
                });
    }

    @Test
    void userDefinedExceptionHandlerBacksOffAutoConfiguration() {
        runner.withUserConfiguration(CustomHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context).hasBean("customGlobalExceptionHandler");
                    assertThat(context).doesNotHaveBean("javalibsGlobalExceptionHandler");
                });
    }

    /**
     * User configuration providing its own exception handler bean.
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomHandlerConfiguration {

        @Bean
        GlobalExceptionHandler customGlobalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
