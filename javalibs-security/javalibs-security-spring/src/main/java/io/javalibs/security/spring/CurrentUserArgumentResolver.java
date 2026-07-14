package io.javalibs.security.spring;

import io.javalibs.security.UserContext;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link HandlerMethodArgumentResolver} injecting the current {@link UserContext} into
 * controller method parameters.
 *
 * <p>Supports every parameter of type {@link UserContext}, whether or not it is annotated with
 * {@link CurrentUser @CurrentUser}. Resolves to {@code null} when the request is
 * unauthenticated, so permit-all endpoints can still declare the parameter.</p>
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return UserContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return UserContextHolder.current().orElse(null);
    }
}
