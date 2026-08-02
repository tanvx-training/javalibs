package io.javalibs.datahub.autoconfigure;

import io.javalibs.datahub.ProcessedEventStore;
import io.javalibs.datahub.spring.idempotency.IdempotentEventProcessor;
import io.javalibs.datahub.spring.idempotency.JdbcProcessedEventStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
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
 *
 * <p>Ordering after {@code DataSourceAutoConfiguration} is load-bearing: the bean
 * below is gated on {@code @ConditionalOnBean(DataSource.class)}, and a
 * {@code @ConditionalOnBean} check only sees bean definitions registered by
 * auto-configurations that have already run. Without it the condition evaluates
 * before Boot registers the {@code DataSource}, so idempotency silently does not
 * wire and duplicate deliveries are processed twice.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
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
