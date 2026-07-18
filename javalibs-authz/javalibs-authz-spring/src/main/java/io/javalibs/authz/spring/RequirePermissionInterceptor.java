package io.javalibs.authz.spring;

import io.javalibs.authz.Scope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Objects;

/**
 * {@link HandlerInterceptor} enforcing {@link RequirePermission @RequirePermission} on
 * controller handler methods and classes, mirroring the RequireRoleInterceptor of
 * javalibs-security. Violations raise Spring Security's AccessDeniedException (403, or
 * 401 for anonymous callers).
 */
public class RequirePermissionInterceptor implements HandlerInterceptor {

    private final PermissionChecker permissionChecker;

    /**
     * Creates a new interceptor with the given {@link PermissionChecker}.
     *
     * @param permissionChecker the permission checker to use; must not be null
     * @throws NullPointerException if permissionChecker is null
     */
    public RequirePermissionInterceptor(PermissionChecker permissionChecker) {
        this.permissionChecker = Objects.requireNonNull(
                permissionChecker, "permissionChecker must not be null");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequirePermission.class);
        }
        if (annotation == null) {
            return true;
        }
        permissionChecker.require(annotation.value(), resolveScope(request, annotation));
        return true;
    }

    /**
     * Resolves the scope from the HTTP request and annotation configuration.
     *
     * @param request the HTTP request
     * @param annotation the {@link RequirePermission} annotation
     * @return the resolved {@link Scope}
     * @throws IllegalStateException if scopeType is declared but scopeIdParam is missing, or if
     *         the path variable specified by scopeIdParam is not found in the request
     */
    private static Scope resolveScope(HttpServletRequest request, RequirePermission annotation) {
        if (annotation.scopeType().isEmpty()) {
            return Scope.GLOBAL;
        }
        if (annotation.scopeIdParam().isEmpty()) {
            throw new IllegalStateException("@RequirePermission(\"" + annotation.value()
                    + "\") declares scopeType='" + annotation.scopeType()
                    + "' but no scopeIdParam. Declare the path variable name holding the scope id.");
        }
        Object rawVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Object scopeId = (rawVariables instanceof Map<?, ?> variables)
                ? variables.get(annotation.scopeIdParam())
                : null;
        if (scopeId == null || scopeId.toString().isBlank()) {
            throw new IllegalStateException("@RequirePermission(\"" + annotation.value()
                    + "\") expects path variable '" + annotation.scopeIdParam()
                    + "' but the request has no such URI template variable. "
                    + "Check the @GetMapping/@PostMapping path.");
        }
        return Scope.of(annotation.scopeType(), scopeId.toString());
    }
}
