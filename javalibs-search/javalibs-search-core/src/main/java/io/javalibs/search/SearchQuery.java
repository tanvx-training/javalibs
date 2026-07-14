package io.javalibs.search;

import java.util.List;

/**
 * A fully parsed, validated search request: filter criteria, how they are combined,
 * sort instructions and paging.
 *
 * <p>Instances are immutable; the criteria and sort lists are defensively copied.</p>
 *
 * @param criteria   the filter criteria (possibly empty, meaning "match all")
 * @param combinator how the criteria are combined ({@code AND} or {@code OR})
 * @param sorts      the sort instructions in priority order (possibly empty)
 * @param page       the zero-based page index (never negative)
 * @param size       the page size (always at least 1)
 */
public record SearchQuery(
        List<SearchCriterion> criteria,
        Combinator combinator,
        List<SortSpec> sorts,
        int page,
        int size) {

    /** Logical combinator applied between filter criteria. */
    public enum Combinator {
        /** All criteria must match. */
        AND,
        /** At least one criterion must match. */
        OR
    }

    /**
     * Normalizes the components: {@code null} lists become empty immutable lists, a
     * {@code null} combinator defaults to {@link Combinator#AND}, the page index is
     * clamped to {@code >= 0} and the size to {@code >= 1}.
     */
    public SearchQuery {
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
        sorts = sorts == null ? List.of() : List.copyOf(sorts);
        combinator = combinator == null ? Combinator.AND : combinator;
        page = Math.max(0, page);
        size = Math.max(1, size);
    }

    /**
     * Returns a query with no criteria and no sorts that matches everything, using
     * page 0 and a page size of 20.
     *
     * @return an empty search query
     */
    public static SearchQuery empty() {
        return new SearchQuery(List.of(), Combinator.AND, List.of(), 0, 20);
    }
}
