package io.javalibs.ddd.spring;

import io.javalibs.ddd.AbstractDomainEvent;
import io.javalibs.ddd.DomainEvent;
import io.javalibs.ddd.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DddAutoConfiguration.class));

    static class SomethingHappened extends AbstractDomainEvent {
    }

    @Configuration
    static class ListenerConfig {
        final List<DomainEvent> received = new ArrayList<>();

        @EventListener
        void on(SomethingHappened event) {
            received.add(event);
        }
    }

    @Test
    void registersPublisherAndDeliversEvents() {
        runner.withUserConfiguration(ListenerConfig.class).run(context -> {
            assertThat(context).hasSingleBean(DomainEventPublisher.class);
            context.getBean(DomainEventPublisher.class).publish(new SomethingHappened());
            assertThat(context.getBean(ListenerConfig.class).received).hasSize(1);
        });
    }

    @Test
    void backsOffWhenUserDefinesPublisher() {
        runner.withUserConfiguration(CustomPublisherConfig.class).run(context -> {
            assertThat(context).hasSingleBean(DomainEventPublisher.class);
            assertThat(context).doesNotHaveBean(SpringDomainEventPublisher.class);
        });
    }

    @Configuration
    static class CustomPublisherConfig {
        @Bean
        DomainEventPublisher customPublisher() {
            return event -> {
            };
        }
    }
}
