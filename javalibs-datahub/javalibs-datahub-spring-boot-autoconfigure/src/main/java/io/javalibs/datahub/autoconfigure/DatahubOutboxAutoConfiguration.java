package io.javalibs.datahub.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalibs.datahub.EventPublisher;
import io.javalibs.datahub.TransactionalOutbox;
import io.javalibs.datahub.spring.outbox.JdbcOutboxRelay;
import io.javalibs.datahub.spring.outbox.JdbcTransactionalOutbox;
import io.javalibs.datahub.spring.outbox.OutboxRelayConfig;
import io.javalibs.datahub.spring.outbox.OutboxRelayScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Opt-in auto-configuration for the Transactional Outbox
 * ({@code javalibs.datahub.outbox.enabled=true}).
 *
 * <p>Prerequisites: a {@link DataSource}, a {@link PlatformTransactionManager}
 * and the outbox table (reference DDL at
 * {@code META-INF/datahub/outbox-schema-postgres.sql}). The relay publishes
 * through whatever {@link EventPublisher} bean is active (Kafka in production,
 * the logging fallback locally).
 *
 * <p>The {@code after}/{@code afterName} ordering is load-bearing, not cosmetic:
 * the beans below are gated on {@code @ConditionalOnBean(DataSource.class)} and
 * {@code @ConditionalOnBean(PlatformTransactionManager.class)}, and a
 * {@code @ConditionalOnBean} check only sees bean definitions registered by
 * auto-configurations that have already run. Without this ordering the conditions
 * evaluate before Boot registers the {@code DataSource} and transaction manager,
 * so the outbox silently does not wire — the application starts healthy and drops
 * every event instead. {@code HibernateJpaAutoConfiguration} is referenced by name
 * because JPA is optional on the classpath.
 */
@AutoConfiguration(
    after = {
        DatahubKafkaAutoConfiguration.class,
        DatahubFallbackAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
    },
    afterName = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ConditionalOnClass({JdbcOperations.class, ObjectMapper.class})
@ConditionalOnProperty(prefix = "javalibs.datahub.outbox", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DatahubProperties.class)
public class DatahubOutboxAutoConfiguration {

  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean(TransactionalOutbox.class)
  public JdbcTransactionalOutbox datahubTransactionalOutbox(DataSource dataSource,
      ObjectProvider<ObjectMapper> objectMapper, DatahubProperties properties) {
    return new JdbcTransactionalOutbox(new JdbcTemplate(dataSource),
        objectMapper.getIfAvailable(ObjectMapper::new), properties.getOutbox().getTable());
  }

  @Bean
  @ConditionalOnBean({DataSource.class, PlatformTransactionManager.class, EventPublisher.class})
  @ConditionalOnMissingBean
  public JdbcOutboxRelay datahubOutboxRelay(DataSource dataSource,
      PlatformTransactionManager transactionManager, EventPublisher eventPublisher,
      ObjectProvider<ObjectMapper> objectMapper, DatahubProperties properties) {
    DatahubProperties.Outbox outbox = properties.getOutbox();
    return new JdbcOutboxRelay(new JdbcTemplate(dataSource),
        new TransactionTemplate(transactionManager), eventPublisher,
        objectMapper.getIfAvailable(ObjectMapper::new),
        new OutboxRelayConfig(outbox.getTable(), outbox.getBatchSize(),
            outbox.getMaxAttempts(), outbox.isUseSkipLocked()));
  }

  @Bean
  @ConditionalOnBean(JdbcOutboxRelay.class)
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "javalibs.datahub.outbox", name = "relay-enabled",
      havingValue = "true", matchIfMissing = true)
  public OutboxRelayScheduler datahubOutboxRelayScheduler(JdbcOutboxRelay relay,
      DatahubProperties properties) {
    return new OutboxRelayScheduler(relay, properties.getOutbox().getPollInterval());
  }
}
