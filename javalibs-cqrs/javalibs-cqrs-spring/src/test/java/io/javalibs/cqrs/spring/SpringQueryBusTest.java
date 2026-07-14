package io.javalibs.cqrs.spring;

import java.util.List;

import io.javalibs.cqrs.DuplicateHandlerException;
import io.javalibs.cqrs.NoHandlerFoundException;
import io.javalibs.cqrs.Query;
import io.javalibs.cqrs.QueryHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SpringQueryBus}.
 */
class SpringQueryBusTest {

    record FindOrderQuery(String id) implements Query<String> {
    }

    record CountOrdersQuery() implements Query<Integer> {
    }

    record UnhandledQuery() implements Query<String> {
    }

    static class FindOrderHandler implements QueryHandler<FindOrderQuery, String> {
        @Override
        public String handle(FindOrderQuery query) {
            return "order-" + query.id();
        }
    }

    static class CountOrdersHandler implements QueryHandler<CountOrdersQuery, Integer> {
        @Override
        public Integer handle(CountOrdersQuery query) {
            return 7;
        }
    }

    @Test
    void askRoutesToTheRightHandlerAndReturnsItsResult() {
        SpringQueryBus bus = new SpringQueryBus(List.of(new FindOrderHandler(), new CountOrdersHandler()));

        assertThat(bus.ask(new FindOrderQuery("123"))).isEqualTo("order-123");
        assertThat(bus.ask(new CountOrdersQuery())).isEqualTo(7);
    }

    @Test
    void askWithoutMatchingHandlerThrowsNoHandlerFoundException() {
        SpringQueryBus bus = new SpringQueryBus(List.of(new FindOrderHandler()));

        assertThatThrownBy(() -> bus.ask(new UnhandledQuery()))
                .isInstanceOf(NoHandlerFoundException.class)
                .hasMessageContaining(UnhandledQuery.class.getName());
    }

    @Test
    void twoHandlersForTheSameQueryThrowDuplicateHandlerException() {
        List<QueryHandler<?, ?>> handlers = List.of(new FindOrderHandler(), new FindOrderHandler());

        assertThatThrownBy(() -> new SpringQueryBus(handlers))
                .isInstanceOf(DuplicateHandlerException.class)
                .hasMessageContaining(FindOrderQuery.class.getName());
    }

    @Test
    void anonymousClassHandlerStillResolves() {
        QueryHandler<CountOrdersQuery, Integer> anonymous =
                new QueryHandler<CountOrdersQuery, Integer>() {
                    @Override
                    public Integer handle(CountOrdersQuery query) {
                        return 99;
                    }
                };
        SpringQueryBus bus = new SpringQueryBus(List.of(anonymous));

        assertThat(bus.ask(new CountOrdersQuery())).isEqualTo(99);
    }
}
