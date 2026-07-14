package io.javalibs.datahub.autoconfigure;

import io.javalibs.datahub.EventPublisher;
import io.javalibs.datahub.spring.LoggingEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration guaranteeing that an {@link EventPublisher} bean always exists.
 *
 * <p>Runs after {@link DatahubKafkaAutoConfiguration}; when neither the Kafka
 * publisher nor a user-defined publisher was registered, a
 * {@link LoggingEventPublisher} is contributed so that code depending on
 * {@link EventPublisher} keeps working in environments without Kafka.</p>
 */
@AutoConfiguration(after = DatahubKafkaAutoConfiguration.class)
public class DatahubFallbackAutoConfiguration {

  /**
   * Creates the logging fallback publisher when no other {@link EventPublisher}
   * bean is present.
   *
   * @return the logging event publisher
   */
  @Bean
  @ConditionalOnMissingBean(EventPublisher.class)
  public LoggingEventPublisher loggingEventPublisher() {
    return new LoggingEventPublisher();
  }
}
