package io.javalibs.observability.autoconfigure;

import io.javalibs.observability.spring.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CorrelationIdWebAutoConfiguration.class));

    @Test
    void registersCorrelationIdFilterByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);

            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(CorrelationIdFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
            assertThat(((CorrelationIdFilter) registration.getFilter()).getHeaderName())
                    .isEqualTo("X-Correlation-Id");
        });
    }

    @Test
    void honorsCustomHeaderProperty() {
        this.contextRunner
                .withPropertyValues("javalibs.observability.correlation.header=X-Request-Id")
                .run(context -> {
                    FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
                    assertThat(((CorrelationIdFilter) registration.getFilter()).getHeaderName())
                            .isEqualTo("X-Request-Id");
                });
    }

    @Test
    void backsOffWhenCorrelationDisabled() {
        this.contextRunner
                .withPropertyValues("javalibs.observability.correlation.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void backsOffOutsideServletWebApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CorrelationIdWebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }
}
