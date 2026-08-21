package io.javalibs.logging.logback;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Turns {@code javalibs.logging.json.enabled=true} into the structured logging
 * property Spring Boot actually reads, so applications never have to spell out
 * the formatter class name.
 *
 * <p>Environment post-processors run before {@code LoggingApplicationListener},
 * which is what makes this work: by the time the logging system is initialised
 * the property is already in place.</p>
 *
 * <p>Only the console target is touched. Console output is what container
 * runtimes collect; writing JSON to a file additionally implies decisions about
 * rotation and disk usage that belong to the application, not to a library.</p>
 */
public class JavalibsLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String ENABLED_PROPERTY = "javalibs.logging.json.enabled";

    static final String CONSOLE_FORMAT_PROPERTY = "logging.structured.format.console";

    static final String PROPERTY_SOURCE_NAME = "javalibsLoggingDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Boolean.TRUE.equals(environment.getProperty(ENABLED_PROPERTY, Boolean.class, Boolean.FALSE))) {
            return;
        }
        if (environment.getProperty(CONSOLE_FORMAT_PROPERTY) != null) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME,
                Map.of(CONSOLE_FORMAT_PROPERTY, JavalibsJsonLogFormatter.class.getName())));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
