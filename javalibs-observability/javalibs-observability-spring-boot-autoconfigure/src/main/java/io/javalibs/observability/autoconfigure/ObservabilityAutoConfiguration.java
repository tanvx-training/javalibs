package io.javalibs.observability.autoconfigure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.javalibs.observability.ObservabilityConstants;
import io.javalibs.observability.spring.MdcTaskDecorator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for javalibs observability core concerns: MDC
 * propagation to asynchronous tasks and common Micrometer metric tags.
 *
 * <p>The {@link TaskDecorator} bean backs off when the application defines its
 * own, and the metrics customizer only activates when Micrometer and the
 * actuator auto-configuration are on the classpath and
 * {@code javalibs.observability.metrics.enabled} is not {@code false}.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    /**
     * MDC-propagating {@link TaskDecorator} picked up by Spring Boot's task
     * executor builder, so {@code @Async} tasks carry the correlation id and
     * trace context of the submitting thread.
     *
     * @return the MDC task decorator
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /**
     * Configures common tags on every {@link MeterRegistry} when Micrometer
     * and the actuator auto-configuration are present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({MeterRegistry.class, MeterRegistryCustomizer.class})
    @ConditionalOnProperty(prefix = "javalibs.observability.metrics", name = "enabled", matchIfMissing = true)
    public static class MetricsCommonTagsConfiguration {

        /**
         * Adds the {@code application} tag (from {@code spring.application.name}),
         * the {@code environment} tag (from
         * {@code javalibs.observability.metrics.environment}) and any tags from
         * {@code javalibs.observability.metrics.common-tags} to every meter.
         * Blank keys/values are skipped and the first occurrence of a tag key
         * wins, so explicit common tags cannot be silently overwritten.
         *
         * @param properties the observability properties
         * @param environment the Spring environment used to resolve the application name
         * @return the customizer applying the common tags
         */
        @Bean
        public MeterRegistryCustomizer<MeterRegistry> javalibsCommonTagsMeterRegistryCustomizer(
                ObservabilityProperties properties, Environment environment) {
            List<Tag> tags = new ArrayList<>();
            Set<String> usedKeys = new HashSet<>();

            String applicationName = environment.getProperty("spring.application.name");
            addTag(tags, usedKeys, ObservabilityConstants.TAG_APPLICATION, applicationName);
            addTag(tags, usedKeys, ObservabilityConstants.TAG_ENVIRONMENT,
                    properties.getMetrics().getEnvironment());
            properties.getMetrics().getCommonTags()
                    .forEach((key, value) -> addTag(tags, usedKeys, key, value));

            return registry -> {
                if (!tags.isEmpty()) {
                    registry.config().commonTags(tags);
                }
            };
        }

        private static void addTag(List<Tag> tags, Set<String> usedKeys, String key, String value) {
            if (StringUtils.hasText(key) && StringUtils.hasText(value) && usedKeys.add(key)) {
                tags.add(Tag.of(key, value));
            }
        }
    }
}
