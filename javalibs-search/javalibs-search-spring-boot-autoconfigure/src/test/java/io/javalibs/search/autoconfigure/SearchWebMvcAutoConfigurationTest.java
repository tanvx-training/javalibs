package io.javalibs.search.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class SearchWebMvcAutoConfigurationTest {

    @Test
    void registersArgumentResolverConfigurerInServletWebApplication() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SearchAutoConfiguration.class, SearchWebMvcAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasBean("searchWebMvcConfigurer");
                    assertThat(context).getBean("searchWebMvcConfigurer")
                            .isInstanceOf(WebMvcConfigurer.class);
                });
    }

    @Test
    void backsOffOutsideWebApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SearchAutoConfiguration.class, SearchWebMvcAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean("searchWebMvcConfigurer"));
    }

    @Test
    void backsOffWithoutSpringMvcOnClasspath() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(WebMvcConfigurer.class))
                .withConfiguration(AutoConfigurations.of(
                        SearchAutoConfiguration.class, SearchWebMvcAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean("searchWebMvcConfigurer"));
    }
}
