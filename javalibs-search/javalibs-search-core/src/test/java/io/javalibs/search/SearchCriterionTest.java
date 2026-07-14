package io.javalibs.search;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCriterionTest {

    @Test
    void acceptsSimpleAndNestedFieldNames() {
        assertThatCode(() -> new SearchCriterion("status", SearchOperator.EQ, List.of("OPEN")))
                .doesNotThrowAnyException();
        assertThatCode(() -> new SearchCriterion("customer.name", SearchOperator.EQ, List.of("x")))
                .doesNotThrowAnyException();
        assertThatCode(() -> new SearchCriterion("created_at2", SearchOperator.NOT_NULL, List.of()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "name;DROP TABLE users",
            "name OR 1=1",
            "1name",
            "_name",
            ".name",
            "name-with-dash",
            "name'",
            " ",
            "na me"
    })
    void rejectsInjectionAttemptsAndGarbageFieldNames(String field) {
        assertThatThrownBy(() -> new SearchCriterion(field, SearchOperator.EQ, List.of("x")))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("Invalid field name");
    }

    @Test
    void rejectsNullFieldName() {
        assertThatThrownBy(() -> new SearchCriterion(null, SearchOperator.EQ, List.of("x")))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("Invalid field name");
    }

    @Test
    void betweenRequiresExactlyTwoValues() {
        assertThatCode(() -> new SearchCriterion("age", SearchOperator.BETWEEN, List.of("1", "9")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new SearchCriterion("age", SearchOperator.BETWEEN, List.of("1")))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("exactly 2");
        assertThatThrownBy(() ->
                new SearchCriterion("age", SearchOperator.BETWEEN, List.of("1", "2", "3")))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("exactly 2");
    }

    @Test
    void inRequiresAtLeastOneValue() {
        assertThatCode(() -> new SearchCriterion("status", SearchOperator.IN, List.of("A")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new SearchCriterion("status", SearchOperator.IN, List.of()))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("at least 1");
        assertThatThrownBy(() -> new SearchCriterion("status", SearchOperator.NOT_IN, List.of()))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("at least 1");
    }

    @Test
    void nullChecksAcceptNoValues() {
        assertThatCode(() -> new SearchCriterion("customer", SearchOperator.IS_NULL, List.of()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() ->
                new SearchCriterion("customer", SearchOperator.IS_NULL, List.of("x")))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("does not accept a value");
        assertThatThrownBy(() ->
                new SearchCriterion("customer", SearchOperator.NOT_NULL, List.of("x")))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("does not accept a value");
    }

    @Test
    void singleValueOperatorsRequireExactlyOneValue() {
        assertThatThrownBy(() -> new SearchCriterion("status", SearchOperator.EQ, List.of()))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("exactly 1");
        assertThatThrownBy(() -> new SearchCriterion("status", SearchOperator.GT, List.of("1", "2")))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("exactly 1");
    }

    @Test
    void nullValuesListIsNormalizedToEmpty() {
        SearchCriterion criterion = new SearchCriterion("x", SearchOperator.IS_NULL, null);
        assertThat(criterion.values()).isEmpty();
    }

    @Test
    void missingOperatorIsRejected() {
        assertThatThrownBy(() -> new SearchCriterion("status", null, List.of("x")))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("Missing operator");
    }
}
