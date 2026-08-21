package io.javalibs.logging.autoconfigure;

import io.javalibs.logging.spring.HttpAccessLogFilter;
import io.javalibs.web.autoconfigure.RequestLoggingAutoConfiguration;
import io.javalibs.web.spring.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class WebRequestLoggingBackOffTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RequestLoggingAutoConfiguration.class, AccessLogAutoConfiguration.class));

    @Test
    void onlyTheStructuredAccessLogFilterIsRegistered() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context.getBean(FilterRegistrationBean.class).getFilter())
                    .isInstanceOf(HttpAccessLogFilter.class);
        });
    }

    @Test
    void theOlderPlainTextFilterIsNotRegisteredAtAll() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RequestLoggingFilter.class));
    }
}
