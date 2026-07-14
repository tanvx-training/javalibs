package io.javalibs.ddd.spring;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Architectural stereotype for stateless domain services: operations that belong
 * to the domain but do not fit naturally on a single aggregate.
 *
 * <p>Meta-annotated with {@link Component} so implementations are picked up by
 * component scanning while the architecture stays visible in the code.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainService {

    @AliasFor(annotation = Component.class)
    String value() default "";
}
