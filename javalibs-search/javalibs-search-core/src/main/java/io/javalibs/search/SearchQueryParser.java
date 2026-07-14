package io.javalibs.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.javalibs.search.SearchQuery.Combinator;
import io.javalibs.search.SortSpec.Direction;

/**
 * Parses raw HTTP query parameters into a {@link SearchQuery}.
 *
 * <p>Supported syntax (parameter names are configurable via {@link ParserConfig}):</p>
 *
 * <ul>
 *   <li>{@code filter=field:op:value} — one criterion per parameter occurrence. The value
 *       part of {@code in}/{@code nin}/{@code between} is split on commas; a literal comma
 *       inside a value can be escaped as {@code \,}. Operators {@code isnull} and
 *       {@code notnull} take no value part ({@code filter=customer:isnull}).</li>
 *   <li>{@code sort=field,asc|desc} or just {@code sort=field} (ascending by default);
 *       repeatable, priority follows parameter order.</li>
 *   <li>{@code page=n} — zero-based page index; negative values are clamped to 0.</li>
 *   <li>{@code size=n} — page size, clamped to {@code [1, maxPageSize]}.</li>
 *   <li>{@code combinator=and|or} — how the criteria are combined (optional).</li>
 * </ul>
 *
 * <p>Any malformed input results in a {@link SearchParseException} whose message pinpoints
 * the offending parameter.</p>
 */
public class SearchQueryParser {

    /** Name of the (non-configurable) combinator query parameter. */
    public static final String COMBINATOR_PARAM = "combinator";

    /**
     * Configuration of the parser: parameter names, paging bounds and the default
     * combinator.
     *
     * @param filterParam       name of the filter parameter (default {@code "filter"})
     * @param sortParam         name of the sort parameter (default {@code "sort"})
     * @param pageParam         name of the page parameter (default {@code "page"})
     * @param sizeParam         name of the size parameter (default {@code "size"})
     * @param defaultPageSize   page size used when the size parameter is absent (default 20)
     * @param maxPageSize       upper bound the requested size is clamped to (default 100)
     * @param defaultCombinator combinator used when the combinator parameter is absent
     *                          (default {@link Combinator#AND})
     */
    public record ParserConfig(
            String filterParam,
            String sortParam,
            String pageParam,
            String sizeParam,
            int defaultPageSize,
            int maxPageSize,
            Combinator defaultCombinator) {

        /**
         * Validates parameter names and paging bounds.
         *
         * @throws IllegalArgumentException if a parameter name is blank or the paging
         *                                  bounds are inconsistent
         */
        public ParserConfig {
            requireNotBlank(filterParam, "filterParam");
            requireNotBlank(sortParam, "sortParam");
            requireNotBlank(pageParam, "pageParam");
            requireNotBlank(sizeParam, "sizeParam");
            if (maxPageSize < 1) {
                throw new IllegalArgumentException("maxPageSize must be >= 1 but was " + maxPageSize);
            }
            if (defaultPageSize < 1 || defaultPageSize > maxPageSize) {
                throw new IllegalArgumentException(
                        "defaultPageSize must be between 1 and maxPageSize (" + maxPageSize
                                + ") but was " + defaultPageSize);
            }
            defaultCombinator = Objects.requireNonNullElse(defaultCombinator, Combinator.AND);
        }

        private static void requireNotBlank(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }

        /**
         * Returns the default configuration:
         * {@code ("filter", "sort", "page", "size", 20, 100, AND)}.
         *
         * @return the default parser configuration
         */
        public static ParserConfig defaults() {
            return new ParserConfig("filter", "sort", "page", "size", 20, 100, Combinator.AND);
        }
    }

    private final ParserConfig config;

    /** Creates a parser using {@link ParserConfig#defaults()}. */
    public SearchQueryParser() {
        this(ParserConfig.defaults());
    }

    /**
     * Creates a parser using the given configuration.
     *
     * @param config the parser configuration, never {@code null}
     */
    public SearchQueryParser(ParserConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Returns the configuration this parser was created with.
     *
     * @return the parser configuration
     */
    public ParserConfig config() {
        return config;
    }

    /**
     * Parses the given query parameters into a {@link SearchQuery}.
     *
     * @param queryParams the raw query parameters, e.g. from
     *                    {@code HttpServletRequest#getParameterMap()}; may be {@code null}
     * @return the parsed search query, never {@code null}
     * @throws SearchParseException if any parameter is malformed
     */
    public SearchQuery parse(Map<String, List<String>> queryParams) {
        Map<String, List<String>> params = queryParams == null ? Map.of() : queryParams;

        List<SearchCriterion> criteria = new ArrayList<>();
        for (String raw : valuesOf(params, config.filterParam())) {
            criteria.add(parseFilter(raw));
        }

        List<SortSpec> sorts = new ArrayList<>();
        for (String raw : valuesOf(params, config.sortParam())) {
            sorts.add(parseSort(raw));
        }

        int page = parsePage(firstOf(params, config.pageParam()));
        int size = parseSize(firstOf(params, config.sizeParam()));
        Combinator combinator = parseCombinator(firstOf(params, COMBINATOR_PARAM));

        return new SearchQuery(criteria, combinator, sorts, page, size);
    }

    private SearchCriterion parseFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SearchParseException(
                    "Empty '" + config.filterParam()
                            + "' parameter: expected syntax 'field:op:value'");
        }
        String[] parts = raw.split(":", 3);
        if (parts.length < 2) {
            throw new SearchParseException(
                    "Malformed filter '" + raw + "': expected syntax 'field:op:value' "
                            + "(or 'field:isnull' / 'field:notnull')");
        }
        String field = parts[0];
        SearchOperator operator = SearchOperator.fromToken(parts[1]);

        List<String> values;
        if (parts.length == 2) {
            if (operator != SearchOperator.IS_NULL && operator != SearchOperator.NOT_NULL) {
                throw new SearchParseException(
                        "Malformed filter '" + raw + "': operator '" + operator.token()
                                + "' requires a value ('field:" + operator.token() + ":value')");
            }
            values = List.of();
        } else {
            if (operator == SearchOperator.IS_NULL || operator == SearchOperator.NOT_NULL) {
                throw new SearchParseException(
                        "Malformed filter '" + raw + "': operator '" + operator.token()
                                + "' does not accept a value");
            }
            values = switch (operator) {
                case IN, NOT_IN, BETWEEN -> splitOnUnescapedCommas(parts[2]);
                default -> List.of(unescapeCommas(parts[2]));
            };
        }
        return new SearchCriterion(field, operator, values);
    }

    private SortSpec parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SearchParseException(
                    "Empty '" + config.sortParam()
                            + "' parameter: expected syntax 'field' or 'field,asc|desc'");
        }
        String[] parts = raw.split(",");
        if (parts.length > 2) {
            throw new SearchParseException(
                    "Malformed sort '" + raw + "': expected syntax 'field' or 'field,asc|desc'");
        }
        String field = parts[0].trim();
        Direction direction = Direction.ASC;
        if (parts.length == 2) {
            String token = parts[1].trim().toLowerCase(Locale.ROOT);
            direction = switch (token) {
                case "asc" -> Direction.ASC;
                case "desc" -> Direction.DESC;
                default -> throw new SearchParseException(
                        "Malformed sort '" + raw + "': direction must be 'asc' or 'desc' but was '"
                                + parts[1].trim() + "'");
            };
        }
        return new SortSpec(field, direction);
    }

    private int parsePage(String raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            throw new SearchParseException(
                    "Invalid '" + config.pageParam() + "' value '" + raw
                            + "': expected a non-negative integer", e);
        }
    }

    private int parseSize(String raw) {
        if (raw == null) {
            return config.defaultPageSize();
        }
        int size;
        try {
            size = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new SearchParseException(
                    "Invalid '" + config.sizeParam() + "' value '" + raw
                            + "': expected an integer between 1 and " + config.maxPageSize(), e);
        }
        return Math.clamp(size, 1, config.maxPageSize());
    }

    private Combinator parseCombinator(String raw) {
        if (raw == null) {
            return config.defaultCombinator();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "and" -> Combinator.AND;
            case "or" -> Combinator.OR;
            default -> throw new SearchParseException(
                    "Invalid '" + COMBINATOR_PARAM + "' value '" + raw
                            + "': expected 'and' or 'or'");
        };
    }

    private static List<String> valuesOf(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        return values == null ? List.of() : values;
    }

    private static String firstOf(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * Splits on commas that are not escaped with a backslash; the escape sequence
     * {@code \,} is unescaped to a literal comma in the resulting tokens.
     */
    private static List<String> splitOnUnescapedCommas(String raw) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == ',') {
                current.append(',');
                i++;
            } else if (c == ',') {
                tokens.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        tokens.add(current.toString());
        return tokens;
    }

    /** Replaces every {@code \,} escape sequence with a literal comma. */
    private static String unescapeCommas(String raw) {
        return raw.replace("\\,", ",");
    }
}
