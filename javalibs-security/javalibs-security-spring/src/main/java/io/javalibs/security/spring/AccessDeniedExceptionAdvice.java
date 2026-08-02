package io.javalibs.security.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders {@link AccessDeniedException} raised <em>inside</em> Spring MVC dispatch as the
 * platform's standard 403 response.
 *
 * <p>Spring Security's {@code ExceptionTranslationFilter} only sees an
 * {@link AccessDeniedException} that unwinds all the way out of the servlet filter chain.
 * {@link RequireRoleInterceptor} and {@code RequirePermissionInterceptor} are Spring MVC
 * {@code HandlerInterceptor}s, so they throw from within {@code DispatcherServlet.doDispatch},
 * where MVC resolves {@code @ExceptionHandler} methods first. When an application also uses
 * javalibs-web, its {@code GlobalExceptionHandler} declares a catch-all
 * {@code @ExceptionHandler(Exception.class)} which matches the denial and turns every
 * {@code @RequireRole} / {@code @RequirePermission} rejection into a 500 — the exception never
 * reaches the filter, so {@link RestAccessDeniedHandler} never runs.
 *
 * <p>This advice closes that gap. It is ordered {@link Ordered#HIGHEST_PRECEDENCE} so it wins
 * against the unordered (effectively lowest-precedence) catch-all, and it delegates to the same
 * {@link RestAccessDeniedHandler} the filter chain uses, so a denial produces byte-identical
 * JSON regardless of whether it was detected before or during dispatch.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessDeniedExceptionAdvice {

    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * Creates the advice.
     *
     * @param accessDeniedHandler the handler used to render the 403 body
     */
    public AccessDeniedExceptionAdvice(RestAccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * Writes the standard 403 response for a denial raised during MVC dispatch.
     *
     * @param ex the denial
     * @param request the current request
     * @param response the current response
     * @throws IOException when the response body cannot be written
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        accessDeniedHandler.handle(request, response, ex);
    }
}
