package io.javalibs.security.spring;

import io.javalibs.security.UserContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Static convenience accessor for the {@link UserContext} of the current thread, backed by
 * Spring Security's {@link SecurityContextHolder}.
 *
 * <p>Prefer injecting the user via {@link CurrentUser @CurrentUser} in controllers; this holder
 * is intended for services and other layers without direct access to web method arguments.</p>
 */
public final class UserContextHolder {

    private UserContextHolder() {
    }

    /**
     * Returns the {@link UserContext} of the currently authenticated caller, if any.
     *
     * @return the current user context, or {@link Optional#empty()} when the request is
     *         unauthenticated or authenticated through a non-javalibs mechanism
     */
    public static Optional<UserContext> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserContext userContext) {
            return Optional.of(userContext);
        }
        return Optional.empty();
    }

    /**
     * Returns the {@link UserContext} of the currently authenticated caller or throws.
     *
     * @return the current user context, never {@code null}
     * @throws IllegalStateException when no authenticated {@link UserContext} is present
     */
    public static UserContext require() {
        return current().orElseThrow(() -> new IllegalStateException(
                "No authenticated UserContext is available on the current thread. "
                        + "Ensure the request passed through the JwtAuthenticationFilter and the "
                        + "endpoint is not permitAll/anonymous."));
    }
}
