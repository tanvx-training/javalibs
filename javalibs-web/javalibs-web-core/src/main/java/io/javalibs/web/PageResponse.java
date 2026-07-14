package io.javalibs.web;

import java.util.List;
import java.util.function.Function;

/**
 * Framework-agnostic pagination envelope.
 *
 * <p>Mirrors the shape of a Spring Data {@code Page} without depending on Spring so that
 * API contracts stay stable regardless of the persistence technology in use. Pages are
 * zero-based.</p>
 *
 * @param <T>           element type
 * @param content       the elements of the current page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of elements across all pages
 * @param totalPages    total number of pages
 * @param hasNext       whether a subsequent page exists
 * @param hasPrevious   whether a preceding page exists
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

    /**
     * Canonical constructor performing a defensive copy of the content list.
     */
    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /**
     * Builds a page response computing the derived fields ({@code totalPages},
     * {@code hasNext}, {@code hasPrevious}) from the given values.
     *
     * @param content       elements of the current page
     * @param page          zero-based page index
     * @param size          requested page size
     * @param totalElements total number of elements across all pages
     * @param <T>           element type
     * @return a fully populated {@link PageResponse}
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / (double) size) : 0;
        boolean hasNext = page + 1 < totalPages;
        boolean hasPrevious = page > 0 && totalPages > 0;
        return new PageResponse<>(content, page, size, totalElements, totalPages, hasNext, hasPrevious);
    }

    /**
     * Returns a new page whose content is the result of applying the given mapping
     * function to each element, preserving all pagination metadata.
     *
     * @param mapper mapping function applied to every element
     * @param <U>    target element type
     * @return a {@link PageResponse} of mapped elements
     */
    public <U> PageResponse<U> map(Function<T, U> mapper) {
        List<U> mapped = content.stream().map(mapper).toList();
        return new PageResponse<>(mapped, page, size, totalElements, totalPages, hasNext, hasPrevious);
    }
}
