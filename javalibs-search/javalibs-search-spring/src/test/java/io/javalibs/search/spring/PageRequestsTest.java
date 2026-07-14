package io.javalibs.search.spring;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import io.javalibs.search.SearchQuery;
import io.javalibs.search.SearchQuery.Combinator;
import io.javalibs.search.SortSpec;
import io.javalibs.search.SortSpec.Direction;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestsTest {

    @Test
    void mapsPageSizeAndSorts() {
        SearchQuery query = new SearchQuery(
                List.of(),
                Combinator.AND,
                List.of(new SortSpec("createdAt", Direction.DESC),
                        new SortSpec("id", Direction.ASC)),
                2, 40);

        PageRequest pageRequest = PageRequests.of(query);

        assertThat(pageRequest.getPageNumber()).isEqualTo(2);
        assertThat(pageRequest.getPageSize()).isEqualTo(40);
        assertThat(pageRequest.getSort()).containsExactly(
                Sort.Order.desc("createdAt"),
                Sort.Order.asc("id"));
    }

    @Test
    void emptySortsYieldUnsorted() {
        PageRequest pageRequest = PageRequests.of(SearchQuery.empty());

        assertThat(pageRequest.getPageNumber()).isZero();
        assertThat(pageRequest.getPageSize()).isEqualTo(20);
        assertThat(pageRequest.getSort().isUnsorted()).isTrue();
    }
}
