package io.javalibs.security.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter of type {@link io.javalibs.security.UserContext} to be
 * resolved to the currently authenticated user by {@link CurrentUserArgumentResolver}.
 *
 * <p>The annotation is optional — parameters typed {@code UserContext} are resolved by type as
 * well — but makes the intent explicit:</p>
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public ProfileDto me(@CurrentUser UserContext user) { ... }
 * }</pre>
 *
 * <p>The resolved value is {@code null} when the request is unauthenticated (e.g. on
 * permit-all endpoints).</p>
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
