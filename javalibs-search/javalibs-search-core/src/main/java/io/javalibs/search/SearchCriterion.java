package io.javalibs.search;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A single filter criterion: a field, an operator and the raw (string) operand values.
 *
 * <p>The field name is validated against {@code ^[a-zA-Z][a-zA-Z0-9_.]*$}, which permits
 * nested property paths such as {@code customer.name} while rejecting anything that could
 * be used for injection or is plainly garbage. The number of values is validated against
 * the arity of the operator:</p>
 *
 * <ul>
 *   <li>{@link SearchOperator#BETWEEN} — exactly 2 values</li>
 *   <li>{@link SearchOperator#IN} / {@link SearchOperator#NOT_IN} — at least 1 value</li>
 *   <li>{@link SearchOperator#IS_NULL} / {@link SearchOperator#NOT_NULL} — no values</li>
 *   <li>all other operators — exactly 1 value</li>
 * </ul>
 *
 * @param field    the (possibly nested) property path the criterion applies to
 * @param operator the comparison operator
 * @param values   the raw string operand values; converted to the field's Java type later
 */
public record SearchCriterion(String field, SearchOperator operator, List<String> values) {

    private static final Pattern FIELD_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.]*$");

    /**
     * Validates the field name, the operator and the operator's arity, and makes the
     * value list immutable.
     *
     * @throws SearchParseException if the field name is invalid, the operator is missing
     *                              or the number of values does not match the operator
     */
    public SearchCriterion {
        if (field == null || !FIELD_PATTERN.matcher(field).matches()) {
            throw new SearchParseException(
                    "Invalid field name '" + field + "': field names must match "
                            + FIELD_PATTERN.pattern());
        }
        if (operator == null) {
            throw new SearchParseException("Missing operator for field '" + field + "'");
        }
        values = values == null ? List.of() : List.copyOf(values);
        int count = values.size();
        switch (operator) {
            case BETWEEN -> {
                if (count != 2) {
                    throw new SearchParseException(
                            "Operator 'between' on field '" + field
                                    + "' requires exactly 2 comma-separated values but got " + count);
                }
            }
            case IN, NOT_IN -> {
                if (count < 1) {
                    throw new SearchParseException(
                            "Operator '" + operator.token() + "' on field '" + field
                                    + "' requires at least 1 value but got none");
                }
            }
            case IS_NULL, NOT_NULL -> {
                if (count != 0) {
                    throw new SearchParseException(
                            "Operator '" + operator.token() + "' on field '" + field
                                    + "' does not accept a value but got " + count);
                }
            }
            default -> {
                if (count != 1) {
                    throw new SearchParseException(
                            "Operator '" + operator.token() + "' on field '" + field
                                    + "' requires exactly 1 value but got " + count);
                }
            }
        }
    }
}
