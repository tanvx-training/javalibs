package io.javalibs.search;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Comparison operators supported by the search filter syntax.
 *
 * <p>Each operator has a lowercase token used in the HTTP filter syntax
 * {@code field:op:value}, e.g. {@code status:eq:OPEN} or {@code total:gte:100}.</p>
 */
public enum SearchOperator {

    /** Equal to a single value ({@code eq}). */
    EQ("eq"),
    /** Not equal to a single value ({@code neq}). */
    NEQ("neq"),
    /** Strictly greater than a single value ({@code gt}). */
    GT("gt"),
    /** Greater than or equal to a single value ({@code gte}). */
    GTE("gte"),
    /** Strictly less than a single value ({@code lt}). */
    LT("lt"),
    /** Less than or equal to a single value ({@code lte}). */
    LTE("lte"),
    /** Case-insensitive "contains" match on the string representation ({@code like}). */
    LIKE("like"),
    /** Negated case-insensitive "contains" match ({@code nlike}). */
    NOT_LIKE("nlike"),
    /** Member of a comma-separated list of values ({@code in}). */
    IN("in"),
    /** Not a member of a comma-separated list of values ({@code nin}). */
    NOT_IN("nin"),
    /** Between two comma-separated values, inclusive ({@code between}). */
    BETWEEN("between"),
    /** The field is {@code null} ({@code isnull}); takes no value. */
    IS_NULL("isnull"),
    /** The field is not {@code null} ({@code notnull}); takes no value. */
    NOT_NULL("notnull");

    private final String token;

    SearchOperator(String token) {
        this.token = token;
    }

    /**
     * Returns the lowercase token used in the HTTP filter syntax for this operator.
     *
     * @return the filter-syntax token, e.g. {@code "gte"}
     */
    public String token() {
        return token;
    }

    /**
     * Resolves an operator from its filter-syntax token, case-insensitively.
     *
     * @param token the token to resolve, e.g. {@code "eq"} or {@code "BETWEEN"}
     * @return the matching operator
     * @throws SearchParseException if the token is {@code null} or unknown; the message
     *                              lists all valid tokens
     */
    public static SearchOperator fromToken(String token) {
        if (token != null) {
            for (SearchOperator operator : values()) {
                if (operator.token.equalsIgnoreCase(token)) {
                    return operator;
                }
            }
        }
        String valid = Arrays.stream(values())
                .map(SearchOperator::token)
                .collect(Collectors.joining(", "));
        throw new SearchParseException(
                "Unknown search operator '" + token + "'. Valid operators are: " + valid);
    }
}
