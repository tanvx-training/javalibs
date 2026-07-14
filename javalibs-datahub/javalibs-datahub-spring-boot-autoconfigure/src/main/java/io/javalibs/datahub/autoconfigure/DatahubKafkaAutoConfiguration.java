package io.javalibs.datahub.autoconfigure;

import io.javalibs.datahub.EventPublisher;
import io.javalibs.datahub.spring.kafka.KafkaEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Auto-configuration registering a Kafka-backed {@link EventPublisher} when
 * spring-kafka is on the classpath, a {@link KafkaTemplate} bean exists and
 * {@code javalibs.datahub.kafka.enabled} is not set to {@code false}.
 *
 * <p>Backs off entirely when the application already defines its own
 * {@link EventPublisher} bean.</p>
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "javalibs.datahub.kafka", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DatahubProperties.class)
public class DatahubKafkaAutoConfiguration {

  /**
   * Creates the Kafka-backed event publisher using the auto-configured
   * {@link KafkaTemplate} and the configured send timeout.
   *
   * @param template   the Kafka template to publish with
   * @param properties the datahub configuration properties
   * @return the Kafka event publisher
   */
  @Bean
  @ConditionalOnMissingBean(EventPublisher.class)
  @ConditionalOnBean(KafkaTemplate.class)
  public KafkaEventPublisher kafkaEventPublisher(
      KafkaTemplate<String, Object> template, DatahubProperties properties) {
    return new KafkaEventPublisher(template, properties.getKafka().getSendTimeout());
  }
}
