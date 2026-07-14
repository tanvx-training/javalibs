package io.javalibs.ddd.spring;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Architectural stereotype for application services (use-case orchestrators):
 * they load aggregates, invoke domain behavior, persist changes and publish the
 * resulting domain events. They contain no business rules themselves.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ApplicationService {

    @AliasFor(annotation = Component.class)
    String value() default "";
}
