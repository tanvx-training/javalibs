package io.javalibs.search;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.javalibs.search.SearchQuery.Combinator;
import io.javalibs.search.SearchQueryParser.ParserConfig;
import io.javalibs.search.SortSpec.Direction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchQueryParserTest {

    private final SearchQueryParser parser = new SearchQueryParser();

    @Nested
    class HappyPaths {

        @Test
        void parsesMultipleFiltersSortsAndPaging() {
            SearchQuery query = parser.parse(Map.of(
                    "filter", List.of("status:eq:OPEN", "total:gte:100", "customerName:like:an"),
                    "sort", List.of("createdAt,desc", "id"),
                    "page", List.of("2"),
                    "size", List.of("50")));

            assertThat(query.criteria()).containsExactly(
                    new SearchCriterion("status", SearchOperator.EQ, List.of("OPEN")),
                    new SearchCriterion("total", SearchOperator.GTE, List.of("100")),
                    new SearchCriterion("customerName", SearchOperator.LIKE, List.of("an")));
            assertThat(query.sorts()).containsExactly(
                    new SortSpec("createdAt", Direction.DESC),
                    new SortSpec("id", Direction.ASC));
            assertThat(query.page()).isEqualTo(2);
            assertThat(query.size()).isEqualTo(50);
            assertThat(query.combinator()).isEqualTo(Combinator.AND);
        }

        @Test
        void parsesInAndBetweenWithCommaSeparatedValues() {
            SearchQuery query = parser.parse(Map.of(
                    "filter", List.of("status:in:NEW,OPEN", "age:between:18,30", "role:nin:ADMIN")));

            assertThat(query.criteria()).containsExactly(
                    new SearchCriterion("status", SearchOperator.IN, List.of("NEW", "OPEN")),
                    new SearchCriterion("age", SearchOperator.BETWEEN, List.of("18", "30")),
                    new SearchCriterion("role", SearchOperator.NOT_IN, List.of("ADMIN")));
        }

        @Test
        void parsesNullChecksWithoutValuePart() {
            SearchQuery query = parser.parse(Map.of(
                    "filter", List.of("customer:isnull", "deletedAt:notnull")));

            assertThat(query.criteria()).containsExactly(
                    new SearchCriterion("customer", SearchOperator.IS_NULL, List.of()),
                    new SearchCriterion("deletedAt", SearchOperator.NOT_NULL, List.of()));
        }

        @Test
        void valueMayContainColons() {
            SearchQuery query = parser.parse(Map.of(
                    "filter", List.of("createdAt:gte:2024-01-01T10:15:30Z")));

            assertThat(query.criteria()).containsExactly(new SearchCriterion(
                    "createdAt", SearchOperator.GTE, List.of("2024-01-01T10:15:30Z")));
        }

        @Test
        void unescapesEscapedCommasInListValues() {
            SearchQuery query = parser.parse(Map.of(
                    "filter", List.of("name:in:Nguyen\\, Van A,Tran\\, Thi B,Le")));

            assertThat(query.criteria().get(0).values())
                    .containsExactly("Nguyen, Van A", "Tran, Thi B", "Le");
        }

        @Test
        void unescapesEscapedCommasInSingleValues() {
            SearchQuery query = parser.parse(Map.of(
                    "filter", List.of("name:eq:Nguyen\\, Van A")));

            assertThat(query.criteria().get(0).values()).containsExactly("Nguyen, Van A");
        }

        @Test
        void emptyInputYieldsDefaults() {
            SearchQuery query = parser.parse(Map.of());

            assertThat(query.criteria()).isEmpty();
            assertThat(query.sorts()).isEmpty();
            assertThat(query.page()).isZero();
            assertThat(query.size()).isEqualTo(20);
            assertThat(query.combinator()).isEqualTo(Combinator.AND);
        }

        @Test
        void nullMapYieldsDefaults() {
            SearchQuery query = parser.parse(null);

            assertThat(query).isEqualTo(SearchQuery.empty());
        }

        @Test
        void parsesOrCombinatorCaseInsensitively() {
            assertThat(parser.parse(Map.of("combinator", List.of("or"))).combinator())
                    .isEqualTo(Combinator.OR);
            assertThat(parser.parse(Map.of("combinator", List.of("AND"))).combinator())
                    .isEqualTo(Combinator.AND);
        }

        @Test
        void clampsSizeAndPageBounds() {
            assertThat(parser.parse(Map.of("size", List.of("9999"))).size()).isEqualTo(100);
            assertThat(parser.parse(Map.of("size", List.of("0"))).size()).isEqualTo(1);
            assertThat(parser.parse(Map.of("size", List.of("-3"))).size()).isEqualTo(1);
            assertThat(parser.parse(Map.of("page", List.of("-5"))).page()).isZero();
        }

        @Test
        void honorsCustomParserConfig() {
            SearchQueryParser custom = new SearchQueryParser(
                    new ParserConfig("q", "s", "p", "sz", 10, 50, Combinator.OR));

            SearchQuery query = custom.parse(Map.of(
                    "q", List.of("status:eq:OPEN"),
                    "s", List.of("id,desc"),
                    "p", List.of("1"),
                    "sz", List.of("200")));

            assertThat(query.criteria()).hasSize(1);
            assertThat(query.sorts()).containsExactly(new SortSpec("id", Direction.DESC));
            assertThat(query.page()).isEqualTo(1);
            assertThat(query.size()).isEqualTo(50);
            assertThat(query.combinator()).isEqualTo(Combinator.OR);

            SearchQuery defaults = custom.parse(Map.of());
            assertThat(defaults.size()).isEqualTo(10);
            assertThat(defaults.combinator()).isEqualTo(Combinator.OR);
        }
    }

    @Nested
    class MalformedInput {

        @Test
        void filterWithoutOperatorIsRejected() {
            assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("justafield"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("justafield")
                    .hasMessageContaining("field:op:value");
        }

        @Test
        void blankFilterIsRejected() {
            assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of(" "))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("filter");
        }

        @Test
        void unknownOperatorIsRejectedWithValidTokens() {
            assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("name:contains:x"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("Unknown search operator 'contains'")
                    .hasMessageContaining("eq")
                    .hasMessageContaining("isnull");
        }

        @Test
        void valueOperatorWithoutValueIsRejected() {
            assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("name:eq"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("requires a value");
        }

        @Test
        void nullCheckWithValueIsRejected() {
            assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("customer:isnull:x"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("does not accept a value");
        }

        @Test
        void betweenWithWrongArityIsRejected() {
            assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("age:between:18"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("exactly 2");
            assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("age:between:1,2,3"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("exactly 2");
        }

        @Test
        void injectionAttemptInFieldNameIsRejected() {
            assertThatThrownBy(() ->
                    parser.parse(Map.of("filter", List.of("name;DROP TABLE users:eq:x"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("Invalid field name");
            assertThatThrownBy(() ->
                    parser.parse(Map.of("sort", List.of("name'--,asc"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("Invalid sort field name");
        }

        @Test
        void malformedSortDirectionIsRejected() {
            assertThatThrownBy(() -> parser.parse(Map.of("sort", List.of("name,sideways"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("'asc' or 'desc'");
        }

        @Test
        void sortWithTooManyPartsIsRejected() {
            assertThatThrownBy(() -> parser.parse(Map.of("sort", List.of("a,asc,extra"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("Malformed sort");
        }

        @Test
        void nonNumericPageAndSizeAreRejected() {
            assertThatThrownBy(() -> parser.parse(Map.of("page", List.of("abc"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("page")
                    .hasMessageContaining("abc");
            assertThatThrownBy(() -> parser.parse(Map.of("size", List.of("xyz"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("size")
                    .hasMessageContaining("xyz");
        }

        @Test
        void unknownCombinatorIsRejected() {
            assertThatThrownBy(() -> parser.parse(Map.of("combinator", List.of("xor"))))
                    .isInstanceOf(SearchParseException.class)
                    .hasMessageContaining("'and' or 'or'");
        }
    }
}
