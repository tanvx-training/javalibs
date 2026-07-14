package io.javalibs.search.spring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import io.javalibs.search.SearchCriterion;
import io.javalibs.search.SearchOperator;
import io.javalibs.search.SearchParseException;
import io.javalibs.search.SearchQuery;
import io.javalibs.search.SearchQuery.Combinator;
import io.javalibs.search.SearchQueryParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real JPA smoke test: builds Specifications from SearchQuery instances and executes them
 * against an embedded H2 database.
 */
@DataJpaTest
class SpecificationBuilderTest {

    @Autowired
    private SampleOrderRepository orders;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void seed() {
        SampleCustomer alice = entityManager.persist(new SampleCustomer("Alice"));
        SampleCustomer bob = entityManager.persist(new SampleCustomer("Bob"));
        entityManager.persist(new SampleOrder(SampleOrder.Status.OPEN, "Alice Nguyen",
                new BigDecimal("150.00"), Instant.parse("2024-01-10T00:00:00Z"), alice));
        entityManager.persist(new SampleOrder(SampleOrder.Status.NEW, "Bob Tran",
                new BigDecimal("50.00"), Instant.parse("2024-02-10T00:00:00Z"), bob));
        entityManager.persist(new SampleOrder(SampleOrder.Status.CLOSED, "Charlie Le",
                new BigDecimal("300.00"), Instant.parse("2024-03-10T00:00:00Z"), null));
        entityManager.flush();
    }

    @Test
    void eqOnEnumField() {
        List<SampleOrder> result = find(query(criterion("status", SearchOperator.EQ, "OPEN")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactly("Alice Nguyen");
    }

    @Test
    void eqOnEnumFieldFallsBackToUppercase() {
        List<SampleOrder> result = find(query(criterion("status", SearchOperator.EQ, "open")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactly("Alice Nguyen");
    }

    @Test
    void likeIsCaseInsensitiveContains() {
        List<SampleOrder> result = find(query(criterion("customerName", SearchOperator.LIKE, "TRAN")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactly("Bob Tran");
    }

    @Test
    void notLikeExcludesMatches() {
        List<SampleOrder> result = find(
                query(criterion("customerName", SearchOperator.NOT_LIKE, "tran")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactlyInAnyOrder("Alice Nguyen", "Charlie Le");
    }

    @Test
    void gteOnBigDecimalField() {
        List<SampleOrder> result = find(query(criterion("total", SearchOperator.GTE, "150")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactlyInAnyOrder("Alice Nguyen", "Charlie Le");
    }

    @Test
    void betweenOnBigDecimalField() {
        List<SampleOrder> result = find(
                query(criterion("total", SearchOperator.BETWEEN, "40", "200")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactlyInAnyOrder("Alice Nguyen", "Bob Tran");
    }

    @Test
    void gteOnInstantField() {
        List<SampleOrder> result = find(
                query(criterion("createdAt", SearchOperator.GTE, "2024-02-01T00:00:00Z")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactlyInAnyOrder("Bob Tran", "Charlie Le");
    }

    @Test
    void inOnEnumField() {
        List<SampleOrder> result = find(
                query(criterion("status", SearchOperator.IN, "NEW", "CLOSED")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactlyInAnyOrder("Bob Tran", "Charlie Le");
    }

    @Test
    void isNullOnToOneAssociation() {
        List<SampleOrder> result = find(query(criterion("customer", SearchOperator.IS_NULL)));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactly("Charlie Le");
    }

    @Test
    void notNullOnToOneAssociation() {
        List<SampleOrder> result = find(query(criterion("customer", SearchOperator.NOT_NULL)));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactlyInAnyOrder("Alice Nguyen", "Bob Tran");
    }

    @Test
    void neqOnEnumField() {
        List<SampleOrder> result = find(query(criterion("status", SearchOperator.NEQ, "OPEN")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactlyInAnyOrder("Bob Tran", "Charlie Le");
    }

    @Test
    void orCombinatorMatchesEitherCriterion() {
        SearchQuery query = new SearchQuery(
                List.of(criterion("status", SearchOperator.EQ, "NEW"),
                        criterion("total", SearchOperator.GTE, "300")),
                Combinator.OR, List.of(), 0, 20);

        assertThat(find(query)).extracting(SampleOrder::getCustomerName)
                .containsExactlyInAnyOrder("Bob Tran", "Charlie Le");
    }

    @Test
    void andCombinatorRequiresAllCriteria() {
        SearchQuery query = new SearchQuery(
                List.of(criterion("status", SearchOperator.EQ, "OPEN"),
                        criterion("total", SearchOperator.GTE, "100")),
                Combinator.AND, List.of(), 0, 20);

        assertThat(find(query)).extracting(SampleOrder::getCustomerName)
                .containsExactly("Alice Nguyen");
    }

    @Test
    void nestedPathTraversesToOneAssociation() {
        List<SampleOrder> result = find(query(criterion("customer.name", SearchOperator.EQ, "Alice")));

        assertThat(result).extracting(SampleOrder::getCustomerName)
                .containsExactly("Alice Nguyen");
    }

    @Test
    void emptyQueryMatchesAllRows() {
        assertThat(find(SearchQuery.empty())).hasSize(3);
    }

    @Test
    void parserOutputPlugsDirectlyIntoSpecification() {
        SearchQuery query = new SearchQueryParser().parse(Map.of(
                "filter", List.of("status:eq:OPEN", "total:gte:100")));

        assertThat(find(query)).extracting(SampleOrder::getCustomerName)
                .containsExactly("Alice Nguyen");
    }

    @Test
    void conversionFailureNamesFieldAndExpectedType() {
        SearchQuery query = query(criterion("total", SearchOperator.GTE, "abc"));

        assertThatThrownBy(() -> find(query))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("total")
                .hasMessageContaining("BigDecimal");
    }

    @Test
    void unknownFieldIsRejectedAtBuildTime() {
        SearchQuery query = query(criterion("nonexistent", SearchOperator.EQ, "x"));

        assertThatThrownBy(() -> find(query))
                .isInstanceOf(SearchParseException.class)
                .hasMessageContaining("Unknown field 'nonexistent'");
    }

    private List<SampleOrder> find(SearchQuery query) {
        return orders.findAll(SpecificationBuilder.toSpecification(query));
    }

    private static SearchQuery query(SearchCriterion... criteria) {
        return new SearchQuery(List.of(criteria), Combinator.AND, List.of(), 0, 20);
    }

    private static SearchCriterion criterion(String field, SearchOperator operator, String... values) {
        return new SearchCriterion(field, operator, List.of(values));
    }
}
