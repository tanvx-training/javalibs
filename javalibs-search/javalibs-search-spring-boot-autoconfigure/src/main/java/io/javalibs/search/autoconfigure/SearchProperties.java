package io.javalibs.search.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.javalibs.search.SearchQuery.Combinator;

/**
 * Configuration properties for javalibs-search under the {@code javalibs.search.*}
 * namespace. Every property maps 1:1 to a component of
 * {@link io.javalibs.search.SearchQueryParser.ParserConfig}.
 */
@ConfigurationProperties("javalibs.search")
public class SearchProperties {

    /** Name of the query parameter carrying filter expressions. */
    private String filterParam = "filter";

    /** Name of the query parameter carrying sort expressions. */
    private String sortParam = "sort";

    /** Name of the query parameter carrying the zero-based page index. */
    private String pageParam = "page";

    /** Name of the query parameter carrying the page size. */
    private String sizeParam = "size";

    /** Page size used when the size parameter is absent. */
    private int defaultPageSize = 20;

    /** Upper bound the requested page size is clamped to. */
    private int maxPageSize = 100;

    /** Combinator applied between filter criteria when none is requested. */
    private Combinator defaultCombinator = Combinator.AND;

    /**
     * Returns the name of the filter query parameter.
     *
     * @return the filter parameter name
     */
    public String getFilterParam() {
        return filterParam;
    }

    /**
     * Sets the name of the filter query parameter.
     *
     * @param filterParam the filter parameter name
     */
    public void setFilterParam(String filterParam) {
        this.filterParam = filterParam;
    }

    /**
     * Returns the name of the sort query parameter.
     *
     * @return the sort parameter name
     */
    public String getSortParam() {
        return sortParam;
    }

    /**
     * Sets the name of the sort query parameter.
     *
     * @param sortParam the sort parameter name
     */
    public void setSortParam(String sortParam) {
        this.sortParam = sortParam;
    }

    /**
     * Returns the name of the page query parameter.
     *
     * @return the page parameter name
     */
    public String getPageParam() {
        return pageParam;
    }

    /**
     * Sets the name of the page query parameter.
     *
     * @param pageParam the page parameter name
     */
    public void setPageParam(String pageParam) {
        this.pageParam = pageParam;
    }

    /**
     * Returns the name of the size query parameter.
     *
     * @return the size parameter name
     */
    public String getSizeParam() {
        return sizeParam;
    }

    /**
     * Sets the name of the size query parameter.
     *
     * @param sizeParam the size parameter name
     */
    public void setSizeParam(String sizeParam) {
        this.sizeParam = sizeParam;
    }

    /**
     * Returns the default page size.
     *
     * @return the default page size
     */
    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    /**
     * Sets the default page size.
     *
     * @param defaultPageSize the default page size
     */
    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    /**
     * Returns the maximum page size.
     *
     * @return the maximum page size
     */
    public int getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * Sets the maximum page size.
     *
     * @param maxPageSize the maximum page size
     */
    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    /**
     * Returns the default combinator.
     *
     * @return the default combinator
     */
    public Combinator getDefaultCombinator() {
        return defaultCombinator;
    }

    /**
     * Sets the default combinator.
     *
     * @param defaultCombinator the default combinator
     */
    public void setDefaultCombinator(Combinator defaultCombinator) {
        this.defaultCombinator = defaultCombinator;
    }
}
