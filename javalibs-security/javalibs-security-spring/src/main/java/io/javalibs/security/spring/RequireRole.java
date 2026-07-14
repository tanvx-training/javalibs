package io.javalibs.security.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the roles required to invoke a controller method (or every method of a controller
 * class). Enforced by {@link RequireRoleInterceptor}; violations raise a Spring Security
 * {@code AccessDeniedException} which the security layer translates into a 403 (or 401 for
 * anonymous callers) response.
 *
 * <p>A method-level annotation takes precedence over a class-level one.</p>
 *
 * <pre>{@code
 * @RequireRole("ADMIN")                                   // any-of (single role)
 * @RequireRole({"ADMIN", "SUPPORT"})                      // any-of (default)
 * @RequireRole(value = {"ADMIN", "AUDITOR"}, anyOf = false) // all-of
 * }</pre>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * The required role names, without the {@code ROLE_} prefix.
     *
     * @return the required roles
     */
    String[] value();

    /**
     * Whether possessing <em>any</em> of the listed roles is sufficient ({@code true}, default)
     * or <em>all</em> of them are required ({@code false}).
     *
     * @return the matching mode
     */
    boolean anyOf() default true;
}
