package io.javalibs.search.spring;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import io.javalibs.search.SearchQuery;
import io.javalibs.search.SortSpec;

/**
 * Maps a {@link SearchQuery} to a Spring Data {@link PageRequest}.
 */
public final class PageRequests {

    private PageRequests() {
    }

    /**
     * Creates a {@link PageRequest} from the query's page index, page size and sort
     * instructions. An empty sort list results in {@link Sort#unsorted()}.
     *
     * @param query the parsed search query, never {@code null}
     * @return the equivalent page request
     */
    public static PageRequest of(SearchQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        List<Sort.Order> orders = query.sorts().stream()
                .map(PageRequests::toOrder)
                .toList();
        Sort sort = orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
        return PageRequest.of(query.page(), query.size(), sort);
    }

    private static Sort.Order toOrder(SortSpec spec) {
        return spec.direction() == SortSpec.Direction.DESC
                ? Sort.Order.desc(spec.field())
                : Sort.Order.asc(spec.field());
    }
}
