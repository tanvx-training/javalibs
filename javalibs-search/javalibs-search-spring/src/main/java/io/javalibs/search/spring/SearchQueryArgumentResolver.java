package io.javalibs.search.spring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import io.javalibs.search.SearchQuery;
import io.javalibs.search.SearchQueryParser;

/**
 * Spring MVC {@link HandlerMethodArgumentResolver} that binds controller method
 * parameters of type {@link SearchQuery} by parsing the request's query parameters
 * with a {@link SearchQueryParser}.
 *
 * <p>Requires Spring Web MVC on the classpath (declared {@code optional} by
 * {@code javalibs-search-spring}).</p>
 */
public class SearchQueryArgumentResolver implements HandlerMethodArgumentResolver {

    private final SearchQueryParser parser;

    /**
     * Creates a resolver delegating to the given parser.
     *
     * @param parser the parser used to interpret request parameters, never {@code null}
     */
    public SearchQueryArgumentResolver(SearchQueryParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return SearchQuery.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        webRequest.getParameterMap().forEach((name, values) -> params.put(name, List.of(values)));
        return parser.parse(params);
    }
}
