package io.javalibs.search;

import java.util.regex.Pattern;

/**
 * A single sort instruction: a field and a direction.
 *
 * <p>The field name is validated with the same whitelist pattern as
 * {@link SearchCriterion} ({@code ^[a-zA-Z][a-zA-Z0-9_.]*$}).</p>
 *
 * @param field     the (possibly nested) property path to sort by
 * @param direction the sort direction
 */
public record SortSpec(String field, Direction direction) {

    private static final Pattern FIELD_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.]*$");

    /** Sort direction. */
    public enum Direction {
        /** Ascending order. */
        ASC,
        /** Descending order. */
        DESC
    }

    /**
     * Validates the field name and direction.
     *
     * @throws SearchParseException if the field name is invalid or the direction is missing
     */
    public SortSpec {
        if (field == null || !FIELD_PATTERN.matcher(field).matches()) {
            throw new SearchParseException(
                    "Invalid sort field name '" + field + "': field names must match "
                            + FIELD_PATTERN.pattern());
        }
        if (direction == null) {
            throw new SearchParseException("Missing sort direction for field '" + field + "'");
        }
    }
}
