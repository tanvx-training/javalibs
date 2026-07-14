package io.javalibs.datahub.autoconfigure;

import io.javalibs.datahub.ProcessedEventStore;
import io.javalibs.datahub.spring.idempotency.IdempotentEventProcessor;
import io.javalibs.datahub.spring.idempotency.JdbcProcessedEventStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Opt-in auto-configuration for idempotent event consumers
 * ({@code javalibs.datahub.idempotency.enabled=true}).
 *
 * <p>Requires a {@link DataSource} and the {@code datahub_processed_event}
 * table (reference DDL at {@code META-INF/datahub/outbox-schema-postgres.sql}).
 */
@AutoConfiguration
@ConditionalOnClass(JdbcOperations.class)
@ConditionalOnProperty(prefix = "javalibs.datahub.idempotency", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DatahubProperties.class)
public class DatahubIdempotencyAutoConfiguration {

  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean(ProcessedEventStore.class)
  public JdbcProcessedEventStore datahubProcessedEventStore(DataSource dataSource,
      DatahubProperties properties) {
    return new JdbcProcessedEventStore(new JdbcTemplate(dataSource),
        properties.getIdempotency().getTable());
  }

  @Bean
  @ConditionalOnBean(ProcessedEventStore.class)
  @ConditionalOnMissingBean
  public IdempotentEventProcessor datahubIdempotentEventProcessor(ProcessedEventStore store) {
    return new IdempotentEventProcessor(store);
  }
}
