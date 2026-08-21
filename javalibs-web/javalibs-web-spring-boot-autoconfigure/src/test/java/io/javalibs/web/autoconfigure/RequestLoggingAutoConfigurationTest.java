package io.javalibs.web.autoconfigure;

import java.util.List;

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
        // A bare @ConditionalOnMissingBean(RequestLoggingFilter.class) only
        // matches a plain RequestLoggingFilter bean; it does not see a
        // FilterRegistrationBean<RequestLoggingFilter> because the condition
        // checks the wrapped generic type, not the container's raw type. The
        // parameterizedContainer attribute closes that gap, so an application
        // that customizes registration (order, URL patterns, init params) by
        // declaring its own FilterRegistrationBean<RequestLoggingFilter> must
        // not end up with a second, auto-configured registration alongside it.
        runner.withUserConfiguration(CustomFilterRegistration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    assertThat(context.getBean("customRequestLoggingFilter", FilterRegistrationBean.class))
                            .isSameAs(context.getBean(FilterRegistrationBean.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFilterRegistration {

        @Bean
        FilterRegistrationBean<RequestLoggingFilter> customRequestLoggingFilter() {
            RequestLoggingFilter filter = new RequestLoggingFilter(false, 2048, List.of());
            return new FilterRegistrationBean<>(filter);
        }
    }
}
