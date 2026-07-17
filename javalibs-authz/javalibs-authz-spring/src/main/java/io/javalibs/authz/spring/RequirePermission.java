package io.javalibs.authz.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission required to invoke a controller method (or every method of a
 * controller class). Method-level annotations take precedence over class-level ones.
 *
 * <pre>{@code
 * @RequirePermission("system.admin")                       // global scope
 * @RequirePermission(value = "issue.read",
 *         scopeType = "project", scopeIdParam = "projectId") // scope from path variable
 * }</pre>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** The required permission code. */
    String value();

    /** Scope type of the check; empty (default) means the global scope. */
    String scopeType() default "";

    /** Name of the path variable carrying the scope id; required when scopeType is set. */
    String scopeIdParam() default "";
}
