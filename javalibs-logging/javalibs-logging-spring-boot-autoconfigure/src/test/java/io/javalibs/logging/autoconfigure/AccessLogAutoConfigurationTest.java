package io.javalibs.logging.autoconfigure;

import java.io.IOException;

import io.javalibs.logging.spring.HttpAccessLogFilter;
import io.javalibs.logging.spring.PrincipalResolver;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
    void normalizesANegativeConfiguredStacktraceMaxLength() {
        webRunner.withPropertyValues("javalibs.logging.stacktrace.max-length=-1").run(context -> {
            LoggingProperties properties = context.getBean(LoggingProperties.class);

            assertThat(properties.stacktrace().maxLength()).isEqualTo(4096);
        });
    }

    @Test
    void theStacktraceRecordItselfNormalizesANegativeMaxLength() {
        assertThat(new LoggingProperties.Stacktrace(true, -5).maxLength()).isEqualTo(4096);
    }

    @Test
    void registersTheFilterLateSoItSeesTheFinalStatus() {
        webRunner.run(context -> {
            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);

            assertThat(registration.getFilter()).isInstanceOf(HttpAccessLogFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Integer.MAX_VALUE - 10);
        });
    }

    @Test
    void backsOffWhenTheApplicationRegistersItsOwnAccessLogFilterRegistration() {
        webRunner.withUserConfiguration(CustomFilterRegistrationConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context.getBean(FilterRegistrationBean.class))
                    .isSameAs(context.getBean(CustomFilterRegistrationConfiguration.class).registration);
        });
    }

    @Test
    void doesNotBackOffForAFilterRegistrationOfAnUnrelatedFilterType() {
        webRunner.withUserConfiguration(UnrelatedFilterRegistrationConfiguration.class).run(context -> {
            // Two registrations coexist: javalibs' own access log filter, plus the
            // application's unrelated one. Only a FilterRegistrationBean<HttpAccessLogFilter>
            // should ever make javalibs back off — a FilterRegistrationBean of some other
            // filter type must not, or @ConditionalOnMissingBean would be matching on the raw
            // FilterRegistrationBean type instead of on its generic parameter.
            assertThat(context.getBeansOfType(FilterRegistrationBean.class)).hasSize(2);
            assertThat(context.getBeansOfType(FilterRegistrationBean.class).values())
                    .extracting(FilterRegistrationBean::getFilter)
                    .hasAtLeastOneElementOfType(HttpAccessLogFilter.class)
                    .hasAtLeastOneElementOfType(SomeOtherFilter.class);
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

    @Configuration(proxyBeanMethods = false)
    static class CustomFilterRegistrationConfiguration {

        private final FilterRegistrationBean<HttpAccessLogFilter> registration =
                new FilterRegistrationBean<>(new HttpAccessLogFilter(null, null, null));

        @Bean
        FilterRegistrationBean<HttpAccessLogFilter> myAccessLogFilter() {
            return this.registration;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnrelatedFilterRegistrationConfiguration {

        @Bean
        FilterRegistrationBean<SomeOtherFilter> someOtherFilter() {
            return new FilterRegistrationBean<>(new SomeOtherFilter());
        }
    }

    static class SomeOtherFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    }
}
