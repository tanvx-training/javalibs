package io.javalibs.spring.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SlowBeanInitializationLoggerTest {

    @Configuration
    static class TestConfig {
        @Bean
        static SlowBeanInitializationLogger slowBeanInitializationLogger() {
            return new SlowBeanInitializationLogger(Duration.ofMillis(1));
        }

        @Bean
        String slowBean() throws InterruptedException {
            Thread.sleep(5);
            return "slow";
        }
    }

    @Test
    void contextStartsAndBeansAreUntouched() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            assertThat(context.getBean("slowBean", String.class)).isEqualTo("slow");
        }
    }
}
