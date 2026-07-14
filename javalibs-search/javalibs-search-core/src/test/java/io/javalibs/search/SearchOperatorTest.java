package io.javalibs.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchOperatorTest {

    @ParameterizedTest
    @CsvSource({
            "eq,EQ", "neq,NEQ", "gt,GT", "gte,GTE", "lt,LT", "lte,LTE",
            "like,LIKE", "nlike,NOT_LIKE", "in,IN", "nin,NOT_IN",
            "between,BETWEEN", "isnull,IS_NULL", "notnull,NOT_NULL"
    })
    void fromTokenResolvesEveryToken(String token, SearchOperator expected) {
        assertThat(SearchOperator.fromToken(token)).isEqualTo(expected);
    }

    @Test
    void fromTokenIsCaseInsensitive() {
        assertThat(SearchOperator.fromToken("EQ")).isEqualTo(SearchOperator.EQ);
        assertThat(SearchOperator.fromToken("Between")).isEqualTo(SearchOperator.BETWEEN);
        assertThat(SearchOperator.fromToken("NLIKE")).isEqualTo(SearchOperator.NOT_LIKE);
    }

    @Test
    void fromTokenRejectsUnknownTokenListingValidOnes() {
        assertThatThrownBy(() -> SearchOperator.fromToken("contains"))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("contains")
                .hasMessageContaining("eq")
                .hasMessageContaining("between")
                .hasMessageContaining("notnull");
    }

    @Test
    void fromTokenRejectsNull() {
        assertThatThrownBy(() -> SearchOperator.fromToken(null))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("Valid operators");
    }
}
