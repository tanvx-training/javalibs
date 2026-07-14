package io.javalibs.datahub.autoconfigure;

import io.javalibs.datahub.EventEnvelope;
import io.javalibs.datahub.EventPublisher;
import io.javalibs.datahub.ProcessedEventStore;
import io.javalibs.datahub.TransactionalOutbox;
import io.javalibs.datahub.spring.idempotency.IdempotentEventProcessor;
import io.javalibs.datahub.spring.outbox.JdbcOutboxRelay;
import io.javalibs.datahub.spring.outbox.OutboxRelayScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DatahubOutboxAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          DatahubFallbackAutoConfiguration.class,
          DatahubOutboxAutoConfiguration.class,
          DatahubIdempotencyAutoConfiguration.class))
      // Large poll interval so the SmartLifecycle scheduler never ticks during tests.
      .withPropertyValues("javalibs.datahub.outbox.poll-interval=10m");

  @Configuration
  static class JdbcInfrastructure {

    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder()
          .setType(EmbeddedDatabaseType.H2)
          .generateUniqueName(true)
          .build();
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }
  }

  @Test
  void outboxBeansPresentWhenEnabled() {
    runner.withUserConfiguration(JdbcInfrastructure.class)
        .withPropertyValues("javalibs.datahub.outbox.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(TransactionalOutbox.class);
          assertThat(context).hasSingleBean(JdbcOutboxRelay.class);
          assertThat(context).hasSingleBean(OutboxRelayScheduler.class);
          assertThat(context).hasSingleBean(EventPublisher.class);
        });
  }

  @Test
  void outboxAbsentByDefault() {
    runner.withUserConfiguration(JdbcInfrastructure.class)
        .run(context -> {
          assertThat(context).doesNotHaveBean(TransactionalOutbox.class);
          assertThat(context).doesNotHaveBean(JdbcOutboxRelay.class);
        });
  }

  @Test
  void relaySchedulerDisabledSeparately() {
    runner.withUserConfiguration(JdbcInfrastructure.class)
        .withPropertyValues(
            "javalibs.datahub.outbox.enabled=true",
            "javalibs.datahub.outbox.relay-enabled=false")
        .run(context -> {
          assertThat(context).hasSingleBean(TransactionalOutbox.class);
          assertThat(context).hasSingleBean(JdbcOutboxRelay.class);
          assertThat(context).doesNotHaveBean(OutboxRelayScheduler.class);
        });
  }

  @Test
  void userOutboxBacksOff() {
    runner.withUserConfiguration(JdbcInfrastructure.class, UserOutbox.class)
        .withPropertyValues("javalibs.datahub.outbox.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(TransactionalOutbox.class);
          assertThat(context.getBean(TransactionalOutbox.class))
              .isNotInstanceOf(io.javalibs.datahub.spring.outbox.JdbcTransactionalOutbox.class);
        });
  }

  @Configuration
  static class UserOutbox {
    @Bean
    TransactionalOutbox customOutbox() {
      return (topic, event) -> {
      };
    }
  }

  @Test
  void idempotencyBeansGatedByFlag() {
    runner.withUserConfiguration(JdbcInfrastructure.class)
        .withPropertyValues("javalibs.datahub.idempotency.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(ProcessedEventStore.class);
          assertThat(context).hasSingleBean(IdempotentEventProcessor.class);
        });
    runner.withUserConfiguration(JdbcInfrastructure.class)
        .run(context -> assertThat(context).doesNotHaveBean(ProcessedEventStore.class));
  }

  @Test
  void outboxRequiresDataSource() {
    runner.withPropertyValues("javalibs.datahub.outbox.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(TransactionalOutbox.class));
  }

  /** Compile-time smoke check that the functional shape of the contracts stays lambda-friendly. */
  @Test
  void contractsAreUsable() {
    TransactionalOutbox outbox = (topic, event) -> {
    };
    outbox.enqueue("t", EventEnvelope.of("E", "s", "payload"));
  }
}
