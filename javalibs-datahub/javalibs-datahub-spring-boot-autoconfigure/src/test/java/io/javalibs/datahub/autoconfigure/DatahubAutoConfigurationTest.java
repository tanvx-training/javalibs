package io.javalibs.datahub.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.javalibs.datahub.EventPublisher;
import io.javalibs.datahub.spring.LoggingEventPublisher;
import io.javalibs.datahub.spring.kafka.KafkaEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

/**
 * Tests for the datahub auto-configurations using {@link ApplicationContextRunner}.
 */
class DatahubAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          DatahubKafkaAutoConfiguration.class,
          DatahubFallbackAutoConfiguration.class,
          DatahubRestAutoConfiguration.class));

  @Test
  void withoutKafkaTemplateFallsBackToLoggingPublisher() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(EventPublisher.class);
      assertThat(context.getBean(EventPublisher.class))
          .isInstanceOf(LoggingEventPublisher.class);
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  void withKafkaTemplateRegistersKafkaPublisher() {
    contextRunner
        .withBean("kafkaTemplate", KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .run(context -> {
          assertThat(context).hasSingleBean(EventPublisher.class);
          assertThat(context.getBean(EventPublisher.class))
              .isInstanceOf(KafkaEventPublisher.class);
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void kafkaDisabledFallsBackToLoggingPublisher() {
    contextRunner
        .withBean("kafkaTemplate", KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .withPropertyValues("javalibs.datahub.kafka.enabled=false")
        .run(context -> {
          assertThat(context).hasSingleBean(EventPublisher.class);
          assertThat(context.getBean(EventPublisher.class))
              .isInstanceOf(LoggingEventPublisher.class);
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void userDefinedEventPublisherBacksOffAutoConfiguration() {
    EventPublisher custom = (topic, event) -> {
    };
    contextRunner
        .withBean("kafkaTemplate", KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .withBean("customEventPublisher", EventPublisher.class, () -> custom)
        .run(context -> {
          assertThat(context).hasSingleBean(EventPublisher.class);
          assertThat(context.getBean(EventPublisher.class)).isSameAs(custom);
          assertThat(context).doesNotHaveBean(KafkaEventPublisher.class);
          assertThat(context).doesNotHaveBean(LoggingEventPublisher.class);
        });
  }

  @Test
  void restBuilderRegisteredByDefault() {
    contextRunner.run(context -> {
      assertThat(context).hasBean("datahubRestClientBuilder");
      assertThat(context.getBean("datahubRestClientBuilder"))
          .isInstanceOf(RestClient.Builder.class);
    });
  }

  @Test
  void restBuilderAbsentWhenRestDisabled() {
    contextRunner
        .withPropertyValues("javalibs.datahub.rest.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("datahubRestClientBuilder"));
  }

  @Test
  void userDefinedRestBuilderBacksOffAutoConfiguration() {
    RestClient.Builder custom = RestClient.builder();
    contextRunner
        .withBean("datahubRestClientBuilder", RestClient.Builder.class, () -> custom)
        .run(context -> assertThat(context.getBean("datahubRestClientBuilder")).isSameAs(custom));
  }
}
