package io.javalibs.security.spring;

import io.javalibs.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * {@link HandlerInterceptor} enforcing {@link RequireRole} annotations on controller handler
 * methods and classes.
 *
 * <p>Resolution order: method-level annotation first, then class-level. When no annotation is
 * present the request is allowed. When present, the caller must be authenticated with a
 * {@link UserContext} and hold the required roles (any-of or all-of depending on
 * {@link RequireRole#anyOf()}); otherwise an
 * {@link org.springframework.security.access.AccessDeniedException} is thrown and left to
 * Spring Security's exception handling (resulting in a 403, or 401 for anonymous callers).</p>
 */
public class RequireRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }

        UserContext user = UserContextHolder.current()
                .orElseThrow(() -> new AccessDeniedException(
                        "Access denied: authentication is required to access this resource"));

        String[] required = requireRole.value();
        boolean allowed = requireRole.anyOf()
                ? user.hasAnyRole(required)
                : Arrays.stream(required).allMatch(user::hasRole);

        if (!allowed) {
            throw new AccessDeniedException("Access denied: caller does not have "
                    + (requireRole.anyOf() ? "any of" : "all of")
                    + " the required roles " + Arrays.toString(required));
        }
        return true;
    }
}
