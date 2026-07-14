package io.javalibs.cqrs.autoconfigure;

import io.javalibs.cqrs.CommandBus;
import io.javalibs.cqrs.CommandHandler;
import io.javalibs.cqrs.QueryBus;
import io.javalibs.cqrs.QueryHandler;
import io.javalibs.cqrs.spring.SpringCommandBus;
import io.javalibs.cqrs.spring.SpringQueryBus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the javalibs CQRS buses.
 *
 * <p>Registers a {@link SpringCommandBus} and a {@link SpringQueryBus} built from
 * every {@link CommandHandler} and {@link QueryHandler} bean found in the
 * application context. Each bus backs off when the application defines its own
 * {@link CommandBus} or {@link QueryBus} bean, and the whole configuration can
 * be switched off with {@code javalibs.cqrs.enabled=false}.
 */
@AutoConfiguration
@EnableConfigurationProperties(CqrsProperties.class)
@ConditionalOnProperty(prefix = "javalibs.cqrs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CqrsAutoConfiguration {

    /**
     * Creates the default {@link CommandBus} from all discovered command handler beans.
     *
     * @param commandHandlers provider for every {@link CommandHandler} bean in the context
     * @return a {@link SpringCommandBus} routing to the discovered handlers
     */
    @Bean
    @ConditionalOnMissingBean(CommandBus.class)
    public SpringCommandBus springCommandBus(ObjectProvider<CommandHandler<?, ?>> commandHandlers) {
        return new SpringCommandBus(commandHandlers.orderedStream().toList());
    }

    /**
     * Creates the default {@link QueryBus} from all discovered query handler beans.
     *
     * @param queryHandlers provider for every {@link QueryHandler} bean in the context
     * @return a {@link SpringQueryBus} routing to the discovered handlers
     */
    @Bean
    @ConditionalOnMissingBean(QueryBus.class)
    public SpringQueryBus springQueryBus(ObjectProvider<QueryHandler<?, ?>> queryHandlers) {
        return new SpringQueryBus(queryHandlers.orderedStream().toList());
    }
}
