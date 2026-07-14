package io.javalibs.spring.context;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationContextProviderTest {

    @Configuration
    static class TestConfig {
        @Bean
        ApplicationContextProvider applicationContextProvider() {
            return new ApplicationContextProvider();
        }

        @Bean
        String greeting() {
            return "hello";
        }
    }

    @Test
    void staticAccessWorksAfterRefresh() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            assertThat(ApplicationContextProvider.context()).isSameAs(context);
            assertThat(ApplicationContextProvider.getBean(String.class)).isEqualTo("hello");
            assertThat(ApplicationContextProvider.getBean("greeting", String.class)).isEqualTo("hello");
        }
    }
}
