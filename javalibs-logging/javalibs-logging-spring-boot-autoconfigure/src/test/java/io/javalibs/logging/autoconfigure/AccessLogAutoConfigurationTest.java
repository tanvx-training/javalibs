package io.javalibs.logging.autoconfigure;

import io.javalibs.logging.spring.HttpAccessLogFilter;
import io.javalibs.logging.spring.PrincipalResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AccessLogAutoConfiguration.class));

    @Test
    void registersTheAccessLogFilterByDefault() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context).hasSingleBean(PrincipalResolver.class);
        });
    }

    @Test
    void backsOffWhenTheFeatureIsDisabled() {
        webRunner.withPropertyValues("javalibs.logging.access.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void doesNothingOutsideAServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AccessLogAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void letsTheApplicationSupplyItsOwnPrincipalResolver() {
        webRunner.withUserConfiguration(CustomResolverConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(PrincipalResolver.class);
            assertThat(context.getBean(PrincipalResolver.class))
                    .isSameAs(context.getBean(CustomResolverConfiguration.class).resolver);
        });
    }

    @Test
    void bindsEveryDocumentedDefault() {
        webRunner.run(context -> {
            LoggingProperties properties = context.getBean(LoggingProperties.class);

            assertThat(properties.json().enabled()).isFalse();
            assertThat(properties.masking().enabled()).isTrue();
            assertThat(properties.masking().value()).isEqualTo("********");
            assertThat(properties.stacktrace().enabled()).isTrue();
            assertThat(properties.stacktrace().maxLength()).isEqualTo(4096);
            assertThat(properties.access().enabled()).isTrue();
            assertThat(properties.access().includeHeaders()).isFalse();
            assertThat(properties.access().includeBody()).isFalse();
            assertThat(properties.access().maxBodyLength()).isEqualTo(2048);
            assertThat(properties.access().trustProxy()).isFalse();
            assertThat(properties.access().slowThresholdMs()).isZero();
            assertThat(properties.access().excludedPaths()).containsExactly("/actuator/**");
            assertThat(properties.access().includedHeaders())
                    .containsExactly("Content-Type", "User-Agent", "Accept");
        });
    }

    @Test
    void registersTheFilterLateSoItSeesTheFinalStatus() {
        webRunner.run(context -> {
            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);

            assertThat(registration.getFilter()).isInstanceOf(HttpAccessLogFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Integer.MAX_VALUE - 10);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomResolverConfiguration {

        private final PrincipalResolver resolver = request -> "fixed-user";

        @Bean
        PrincipalResolver principalResolver() {
            return this.resolver;
        }
    }
}
