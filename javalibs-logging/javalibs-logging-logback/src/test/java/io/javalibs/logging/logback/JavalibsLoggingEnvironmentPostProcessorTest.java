package io.javalibs.logging.logback;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsLoggingEnvironmentPostProcessorTest {

    private final JavalibsLoggingEnvironmentPostProcessor processor =
            new JavalibsLoggingEnvironmentPostProcessor();

    private static ConfigurableEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    @Test
    void pointsStructuredLoggingAtTheJavalibsFormatterWhenEnabled() {
        ConfigurableEnvironment environment =
                environmentWith(Map.of("javalibs.logging.json.enabled", "true"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo(JavalibsJsonLogFormatter.class.getName());
    }

    @Test
    void staysOutOfTheWayWhenNotEnabled() {
        ConfigurableEnvironment environment = environmentWith(Map.of());

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.console")).isNull();
    }

    @Test
    void neverOverridesAnExplicitApplicationChoice() {
        ConfigurableEnvironment environment = environmentWith(Map.of(
                "javalibs.logging.json.enabled", "true",
                "logging.structured.format.console", "ecs"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }

    @Test
    void leavesFileLoggingAlone() {
        ConfigurableEnvironment environment =
                environmentWith(Map.of("javalibs.logging.json.enabled", "true"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.file")).isNull();
    }

    @Test
    void isRegisteredForSpringBootToPickUp() {
        assertThat(getClass().getClassLoader().getResource("META-INF/spring.factories")).isNotNull();
    }
}
