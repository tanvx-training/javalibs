package io.javalibs.web;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PageResponse#of(List, int, int, long)} math and {@link PageResponse#map}.
 */
class PageResponseTest {

    @Test
    void computesTotalPagesRoundingUp() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b", "c"), 0, 3, 10);

        assertThat(page.totalPages()).isEqualTo(4);
        assertThat(page.totalElements()).isEqualTo(10);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(3);
    }

    @Test
    void firstPageHasNextButNoPrevious() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b"), 0, 2, 5);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    void middlePageHasNextAndPrevious() {
        PageResponse<String> page = PageResponse.of(List.of("c", "d"), 1, 2, 5);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void lastPageHasPreviousButNoNext() {
        PageResponse<String> page = PageResponse.of(List.of("e"), 2, 2, 5);

        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void exactMultipleOfSizeDoesNotAddExtraPage() {
        PageResponse<String> page = PageResponse.of(List.of("c", "d"), 1, 2, 4);

        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void emptyResultHasZeroPagesAndNoNavigation() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 10, 0);

        assertThat(page.totalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.content()).isEmpty();
    }

    @Test
    void zeroSizeYieldsZeroTotalPages() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 0, 7);

        assertThat(page.totalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void mapTransformsContentAndKeepsMetadata() {
        PageResponse<String> page = PageResponse.of(List.of("1", "2"), 1, 2, 6);

        PageResponse<Integer> mapped = page.map(Integer::valueOf);

        assertThat(mapped.content()).containsExactly(1, 2);
        assertThat(mapped.page()).isEqualTo(1);
        assertThat(mapped.size()).isEqualTo(2);
        assertThat(mapped.totalElements()).isEqualTo(6);
        assertThat(mapped.totalPages()).isEqualTo(3);
        assertThat(mapped.hasNext()).isTrue();
        assertThat(mapped.hasPrevious()).isTrue();
    }
}
