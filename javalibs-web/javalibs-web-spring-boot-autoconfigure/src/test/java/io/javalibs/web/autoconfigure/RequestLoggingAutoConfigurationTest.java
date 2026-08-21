package io.javalibs.web.autoconfigure;

import io.javalibs.web.spring.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RequestLoggingAutoConfiguration.class));

    @Test
    void registersTheFilterWhenJavalibsLoggingIsNotOnTheClasspath() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context.getBean(FilterRegistrationBean.class).getFilter())
                    .isInstanceOf(RequestLoggingFilter.class);
        });
    }

    @Test
    void backsOffWhenDisabledByProperty() {
        runner.withPropertyValues("javalibs.web.logging.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void backsOffWhenTheApplicationDeclaresItsOwnFilterRegistrationBean() {
        runner.withUserConfiguration(CustomFilterRegistrationConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(FilterRegistrationBean.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFilterRegistrationConfiguration {

        @Bean
        FilterRegistrationBean<RequestLoggingFilter> customRequestLoggingFilter() {
            return new FilterRegistrationBean<>(new RequestLoggingFilter());
        }
    }
}
