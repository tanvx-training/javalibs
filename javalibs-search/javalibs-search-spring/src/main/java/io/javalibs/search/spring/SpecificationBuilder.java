package io.javalibs.search.spring;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import io.javalibs.search.SearchCriterion;
import io.javalibs.search.SearchParseException;
import io.javalibs.search.SearchQuery;
import io.javalibs.search.SearchQuery.Combinator;

/**
 * Translates a {@link SearchQuery} into a Spring Data JPA {@link Specification}.
 *
 * <p>Field paths may be nested ({@code customer.name}); each segment is resolved with
 * {@link Path#get(String)}, which supports embedded attributes and to-one associations
 * (the JPA provider creates an implicit join). Collection-valued associations are
 * <strong>not</strong> supported.</p>
 *
 * <p>String operand values are converted to the Java type of the target attribute
 * (see {@link Path#getJavaType()}). Supported types: {@code String}, {@code UUID},
 * {@code Boolean}, {@code Integer}, {@code Long}, {@code Short}, {@code Float},
 * {@code Double}, {@code BigDecimal}, {@code BigInteger}, {@code LocalDate},
 * {@code LocalTime}, {@code LocalDateTime}, {@code Instant}, {@code OffsetDateTime}
 * (all ISO-8601) and any enum (exact constant name, falling back to uppercase).
 * A conversion failure raises a {@link SearchParseException} naming the field and the
 * expected type.</p>
 *
 * <p>All values are bound through the Criteria API, so user input never reaches the SQL
 * text and SQL injection is structurally impossible.</p>
 */
public final class SpecificationBuilder {

    private SpecificationBuilder() {
    }

    /**
     * Builds a {@link Specification} matching all criteria of the given query, combined
     * with the query's {@link Combinator}. An empty criteria list yields a specification
     * that matches every row.
     *
     * @param <T>   the root entity type
     * @param query the parsed search query, never {@code null}
     * @return a specification equivalent to the query's criteria
     */
    public static <T> Specification<T> toSpecification(SearchQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return (root, criteriaQuery, cb) -> {
            if (query.criteria().isEmpty()) {
                return cb.conjunction();
            }
            Predicate[] predicates = query.criteria().stream()
                    .map(criterion -> toPredicate(criterion, root, cb))
                    .toArray(Predicate[]::new);
            return query.combinator() == Combinator.OR ? cb.or(predicates) : cb.and(predicates);
        };
    }

    private static Predicate toPredicate(SearchCriterion criterion, Root<?> root, CriteriaBuilder cb) {
        Path<?> path = resolvePath(root, criterion.field());
        String field = criterion.field();
        List<String> values = criterion.values();
        return switch (criterion.operator()) {
            case EQ -> cb.equal(path, convert(values.get(0), path, field));
            case NEQ -> cb.notEqual(path, convert(values.get(0), path, field));
            case GT, GTE, LT, LTE ->
                    comparablePredicate(cb, path, criterion, convert(values.get(0), path, field));
            case LIKE -> cb.like(cb.lower(path.as(String.class)), containsPattern(values.get(0)));
            case NOT_LIKE ->
                    cb.notLike(cb.lower(path.as(String.class)), containsPattern(values.get(0)));
            case IN -> path.in(convertAll(values, path, field));
            case NOT_IN -> cb.not(path.in(convertAll(values, path, field)));
            case BETWEEN -> betweenPredicate(cb, path, criterion);
            case IS_NULL -> cb.isNull(path);
            case NOT_NULL -> cb.isNotNull(path);
        };
    }

    /**
     * Resolves a dot-separated property path from the root entity. To-one associations
     * and embedded attributes are traversed via {@link Path#get(String)}.
     */
    private static Path<?> resolvePath(Root<?> root, String field) {
        Path<?> path = root;
        for (String part : field.split("\\.")) {
            try {
                path = path.get(part);
            } catch (IllegalArgumentException e) {
                throw new SearchParseException(
                        "Unknown field '" + field + "' on entity "
                                + root.getJavaType().getSimpleName()
                                + ": attribute '" + part + "' does not exist", e);
            }
        }
        return path;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate comparablePredicate(
            CriteriaBuilder cb, Path<?> path, SearchCriterion criterion, Object value) {
        Comparable comparable = asComparable(value, path, criterion);
        Expression expression = path;
        return switch (criterion.operator()) {
            case GT -> cb.greaterThan(expression, comparable);
            case GTE -> cb.greaterThanOrEqualTo(expression, comparable);
            case LT -> cb.lessThan(expression, comparable);
            case LTE -> cb.lessThanOrEqualTo(expression, comparable);
            default -> throw new IllegalStateException(
                    "Not a comparison operator: " + criterion.operator());
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate betweenPredicate(CriteriaBuilder cb, Path<?> path, SearchCriterion criterion) {
        String field = criterion.field();
        Comparable lower = asComparable(convert(criterion.values().get(0), path, field), path, criterion);
        Comparable upper = asComparable(convert(criterion.values().get(1), path, field), path, criterion);
        Expression expression = path;
        return cb.between(expression, lower, upper);
    }

    @SuppressWarnings("rawtypes")
    private static Comparable asComparable(Object value, Path<?> path, SearchCriterion criterion) {
        if (value instanceof Comparable comparable) {
            return comparable;
        }
        throw new SearchParseException(
                "Field '" + criterion.field() + "' of type " + path.getJavaType().getSimpleName()
                        + " does not support operator '" + criterion.operator().token() + "'");
    }

    private static String containsPattern(String value) {
        return "%" + value.toLowerCase(Locale.ROOT) + "%";
    }

    private static List<Object> convertAll(List<String> values, Path<?> path, String field) {
        return values.stream().map(value -> convert(value, path, field)).toList();
    }

    /**
     * Converts a raw string operand to the Java type of the target attribute.
     *
     * @throws SearchParseException if the value cannot be converted or the attribute
     *                              type is unsupported
     */
    private static Object convert(String value, Path<?> path, String field) {
        Class<?> type = path.getJavaType();
        try {
            if (type == String.class || type == Object.class || type == char[].class) {
                return value;
            }
            if (type == UUID.class) {
                return UUID.fromString(value);
            }
            if (type == Boolean.class || type == boolean.class) {
                if ("true".equalsIgnoreCase(value)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(value)) {
                    return Boolean.FALSE;
                }
                throw new IllegalArgumentException("expected 'true' or 'false'");
            }
            if (type == Integer.class || type == int.class) {
                return Integer.valueOf(value);
            }
            if (type == Long.class || type == long.class) {
                return Long.valueOf(value);
            }
            if (type == Short.class || type == short.class) {
                return Short.valueOf(value);
            }
            if (type == Float.class || type == float.class) {
                return Float.valueOf(value);
            }
            if (type == Double.class || type == double.class) {
                return Double.valueOf(value);
            }
            if (type == BigDecimal.class) {
                return new BigDecimal(value);
            }
            if (type == BigInteger.class) {
                return new BigInteger(value);
            }
            if (type == LocalDate.class) {
                return LocalDate.parse(value);
            }
            if (type == LocalTime.class) {
                return LocalTime.parse(value);
            }
            if (type == LocalDateTime.class) {
                return LocalDateTime.parse(value);
            }
            if (type == Instant.class) {
                return Instant.parse(value);
            }
            if (type == OffsetDateTime.class) {
                return OffsetDateTime.parse(value);
            }
            if (type.isEnum()) {
                return enumValue(type, value);
            }
        } catch (RuntimeException e) {
            throw new SearchParseException(
                    "Cannot convert value '" + value + "' for field '" + field
                            + "': expected type " + type.getSimpleName(), e);
        }
        throw new SearchParseException(
                "Field '" + field + "' has unsupported type " + type.getName()
                        + " for value conversion");
    }

    /** Resolves an enum constant by exact name, falling back to the uppercase name. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String value) {
        try {
            return Enum.valueOf((Class<? extends Enum>) type, value);
        } catch (IllegalArgumentException e) {
            return Enum.valueOf((Class<? extends Enum>) type, value.toUpperCase(Locale.ROOT));
        }
    }
}
