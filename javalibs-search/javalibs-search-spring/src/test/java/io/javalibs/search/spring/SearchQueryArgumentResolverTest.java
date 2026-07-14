package io.javalibs.search.spring;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import io.javalibs.search.SearchCriterion;
import io.javalibs.search.SearchOperator;
import io.javalibs.search.SearchQuery;
import io.javalibs.search.SearchQueryParser;
import io.javalibs.search.SortSpec;
import io.javalibs.search.SortSpec.Direction;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryArgumentResolverTest {

    private final SearchQueryArgumentResolver resolver =
            new SearchQueryArgumentResolver(new SearchQueryParser());

    @SuppressWarnings("unused")
    void handler(SearchQuery query, String other) {
    }

    @Test
    void supportsOnlySearchQueryParameters() throws Exception {
        Method method = getClass().getDeclaredMethod("handler", SearchQuery.class, String.class);

        assertThat(resolver.supportsParameter(new MethodParameter(method, 0))).isTrue();
        assertThat(resolver.supportsParameter(new MethodParameter(method, 1))).isFalse();
    }

    @Test
    void resolvesSearchQueryFromRequestParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("filter", "status:eq:OPEN");
        request.addParameter("filter", "total:gte:100");
        request.addParameter("sort", "createdAt,desc");
        request.addParameter("page", "1");
        request.addParameter("size", "10");

        Method method = getClass().getDeclaredMethod("handler", SearchQuery.class, String.class);
        Object resolved = resolver.resolveArgument(
                new MethodParameter(method, 0), null, new ServletWebRequest(request), null);

        assertThat(resolved).isInstanceOf(SearchQuery.class);
        SearchQuery query = (SearchQuery) resolved;
        assertThat(query.criteria()).containsExactly(
                new SearchCriterion("status", SearchOperator.EQ, List.of("OPEN")),
                new SearchCriterion("total", SearchOperator.GTE, List.of("100")));
        assertThat(query.sorts()).containsExactly(new SortSpec("createdAt", Direction.DESC));
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(10);
    }
}
