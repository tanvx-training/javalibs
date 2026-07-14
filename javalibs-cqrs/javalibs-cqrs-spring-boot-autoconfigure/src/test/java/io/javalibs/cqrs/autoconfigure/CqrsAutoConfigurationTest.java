package io.javalibs.cqrs.autoconfigure;

import java.util.UUID;

import io.javalibs.cqrs.Command;
import io.javalibs.cqrs.CommandBus;
import io.javalibs.cqrs.CommandHandler;
import io.javalibs.cqrs.Query;
import io.javalibs.cqrs.QueryBus;
import io.javalibs.cqrs.QueryHandler;
import io.javalibs.cqrs.spring.SpringCommandBus;
import io.javalibs.cqrs.spring.SpringQueryBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CqrsAutoConfiguration} using {@link ApplicationContextRunner}.
 */
class CqrsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CqrsAutoConfiguration.class));

    record CreateOrderCommand(String product) implements Command<UUID> {
    }

    record FindOrderQuery(String id) implements Query<String> {
    }

    static class CreateOrderHandler implements CommandHandler<CreateOrderCommand, UUID> {
        static final UUID FIXED_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

        @Override
        public UUID handle(CreateOrderCommand command) {
            return FIXED_ID;
        }
    }

    static class FindOrderHandler implements QueryHandler<FindOrderQuery, String> {
        @Override
        public String handle(FindOrderQuery query) {
            return "order-" + query.id();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HandlersConfiguration {

        @Bean
        CreateOrderHandler createOrderHandler() {
            return new CreateOrderHandler();
        }

        @Bean
        FindOrderHandler findOrderHandler() {
            return new FindOrderHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBusConfiguration {

        @Bean
        CommandBus customCommandBus() {
            return new CommandBus() {
                @Override
                public <R> R dispatch(Command<R> command) {
                    throw new UnsupportedOperationException("custom bus");
                }
            };
        }
    }

    @Test
    void autoConfigurationRegistersFunctionalBuses() {
        runner.withUserConfiguration(HandlersConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CommandBus.class);
                    assertThat(context).hasSingleBean(QueryBus.class);
                    assertThat(context).hasSingleBean(CqrsProperties.class);
                    assertThat(context.getBean(CqrsProperties.class).enabled()).isTrue();

                    CommandBus commandBus = context.getBean(CommandBus.class);
                    QueryBus queryBus = context.getBean(QueryBus.class);
                    assertThat(commandBus.dispatch(new CreateOrderCommand("book")))
                            .isEqualTo(CreateOrderHandler.FIXED_ID);
                    assertThat(queryBus.ask(new FindOrderQuery("123"))).isEqualTo("order-123");
                });
    }

    @Test
    void disabledPropertySwitchesOffAllBuses() {
        runner.withUserConfiguration(HandlersConfiguration.class)
                .withPropertyValues("javalibs.cqrs.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CommandBus.class);
                    assertThat(context).doesNotHaveBean(QueryBus.class);
                });
    }

    @Test
    void userDefinedCommandBusMakesAutoConfigurationBackOff() {
        runner.withUserConfiguration(HandlersConfiguration.class, CustomBusConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CommandBus.class);
                    assertThat(context).doesNotHaveBean(SpringCommandBus.class);
                    assertThat(context.getBean(CommandBus.class))
                            .isSameAs(context.getBean("customCommandBus"));
                    // The query bus is independent and still auto-configured.
                    assertThat(context).hasSingleBean(SpringQueryBus.class);
                });
    }

    @Test
    void contextWithoutHandlersStillProvidesEmptyBuses() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CommandBus.class);
            assertThat(context).hasSingleBean(QueryBus.class);
        });
    }
}
