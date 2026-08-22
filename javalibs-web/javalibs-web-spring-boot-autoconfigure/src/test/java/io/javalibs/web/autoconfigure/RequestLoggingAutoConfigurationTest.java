package io.javalibs.web.autoconfigure;

import java.io.IOException;
import java.util.List;

import io.javalibs.web.spring.RequestLoggingFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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

    @Test
    void backsOffWhenTheApplicationDeclaresABareFilterBean() {
        // Pre-existing behavior that must survive the parameterizedContainer
        // change: a plain RequestLoggingFilter bean, not wrapped in any
        // FilterRegistrationBean, has always been enough to make the
        // auto-configured registration back off (the original
        // @ConditionalOnMissingBean(RequestLoggingFilter.class) matched it
        // directly). parameterizedContainer = FilterRegistrationBean.class
        // widens the condition to also catch a
        // FilterRegistrationBean<RequestLoggingFilter> (see
        // backsOffWhenTheApplicationDeclaresItsOwnFilterRegistrationBean
        // above), but must not narrow it -- this bare-bean case has to keep
        // working exactly as before.
        runner.withUserConfiguration(CustomBareFilter.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                    assertThat(context).hasSingleBean(RequestLoggingFilter.class);
                });
    }

    @Test
    void doesNotBackOffForAFilterRegistrationOfAnUnrelatedFilterType() {
        // Two registrations must coexist: javalibs' own request logging
        // filter, plus the application's unrelated one. A
        // FilterRegistrationBean of some other filter type must not satisfy
        // @ConditionalOnMissingBean(value = RequestLoggingFilter.class,
        // parameterizedContainer = FilterRegistrationBean.class) -- otherwise
        // the condition would be matching on the raw FilterRegistrationBean
        // container type instead of on its generic parameter, and any
        // unrelated filter registration present in the application would
        // silently suppress javalibs' own request logging filter.
        runner.withUserConfiguration(UnrelatedFilterRegistration.class)
                .run(context -> {
                    assertThat(context.getBeansOfType(FilterRegistrationBean.class)).hasSize(2);
                    assertThat(context.getBeansOfType(FilterRegistrationBean.class).values())
                            .extracting(FilterRegistrationBean::getFilter)
                            .hasAtLeastOneElementOfType(RequestLoggingFilter.class)
                            .hasAtLeastOneElementOfType(SomeOtherFilter.class);
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

    @Configuration(proxyBeanMethods = false)
    static class CustomBareFilter {

        @Bean
        RequestLoggingFilter customRequestLoggingFilter() {
            return new RequestLoggingFilter(false, 2048, List.of());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnrelatedFilterRegistration {

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
