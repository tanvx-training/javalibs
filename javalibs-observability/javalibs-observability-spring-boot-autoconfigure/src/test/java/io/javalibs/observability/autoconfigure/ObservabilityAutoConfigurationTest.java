package io.javalibs.observability.autoconfigure;

import io.javalibs.observability.spring.MdcTaskDecorator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskDecorator;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void registersMdcTaskDecoratorByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskDecorator.class);
            assertThat(context.getBean(TaskDecorator.class)).isInstanceOf(MdcTaskDecorator.class);
        });
    }

    @Test
    void backsOffWhenUserDefinesTaskDecorator() {
        TaskDecorator custom = runnable -> runnable;
        this.contextRunner
                .withBean("customTaskDecorator", TaskDecorator.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskDecorator.class);
                    assertThat(context.getBean(TaskDecorator.class)).isSameAs(custom);
                    assertThat(context).doesNotHaveBean("mdcTaskDecorator");
                });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void meterRegistryCustomizerAppliesApplicationEnvironmentAndCustomTags() {
        this.contextRunner
                .withPropertyValues(
                        "spring.application.name=test-app",
                        "javalibs.observability.metrics.environment=prod",
                        "javalibs.observability.metrics.common-tags.team=platform")
                .run(context -> {
                    assertThat(context).hasSingleBean(MeterRegistryCustomizer.class);

                    SimpleMeterRegistry registry = new SimpleMeterRegistry();
                    MeterRegistryCustomizer<MeterRegistry> customizer =
                            (MeterRegistryCustomizer) context.getBean(MeterRegistryCustomizer.class);
                    customizer.customize(registry);

                    Counter counter = registry.counter("test.counter");
                    assertThat(counter.getId().getTag("application")).isEqualTo("test-app");
                    assertThat(counter.getId().getTag("environment")).isEqualTo("prod");
                    assertThat(counter.getId().getTag("team")).isEqualTo("platform");
                });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void skipsAbsentApplicationNameAndDuplicateTagKeys() {
        this.contextRunner
                .withPropertyValues(
                        "javalibs.observability.metrics.environment=dev",
                        "javalibs.observability.metrics.common-tags.environment=overridden")
                .run(context -> {
                    SimpleMeterRegistry registry = new SimpleMeterRegistry();
                    MeterRegistryCustomizer<MeterRegistry> customizer =
                            (MeterRegistryCustomizer) context.getBean(MeterRegistryCustomizer.class);
                    customizer.customize(registry);

                    Counter counter = registry.counter("test.counter");
                    assertThat(counter.getId().getTag("application")).isNull();
                    assertThat(counter.getId().getTag("environment")).isEqualTo("dev");
                });
    }

    @Test
    void metricsCustomizerBacksOffWhenDisabled() {
        this.contextRunner
                .withPropertyValues("javalibs.observability.metrics.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MeterRegistryCustomizer.class));
    }

    @Test
    void metricsCustomizerBacksOffWithoutMicrometerOnClasspath() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("javalibsCommonTagsMeterRegistryCustomizer");
                    assertThat(context).hasSingleBean(TaskDecorator.class);
                });
    }
}
