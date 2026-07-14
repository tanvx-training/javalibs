package io.javalibs.ddd.spring;

import io.javalibs.ddd.DomainEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Registers a {@link DomainEventPublisher} backed by Spring's event bus when the
 * application does not define one. Kept inside javalibs-ddd-spring (instead of a
 * dedicated autoconfigure module) because it registers a single unconditional
 * bean; the spring-boot-autoconfigure dependency is optional.
 */
@AutoConfiguration
public class DddAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    public SpringDomainEventPublisher domainEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringDomainEventPublisher(publisher);
    }
}
