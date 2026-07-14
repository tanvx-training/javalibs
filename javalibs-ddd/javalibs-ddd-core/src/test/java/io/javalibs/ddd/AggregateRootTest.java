package io.javalibs.ddd;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregateRootTest {

    static class OrderPlaced extends AbstractDomainEvent {
    }

    static class Order extends AggregateRoot<UUID> {
        private final UUID id;

        Order(UUID id) {
            this.id = id;
        }

        @Override
        public UUID getId() {
            return id;
        }

        void place() {
            registerEvent(new OrderPlaced());
        }
    }

    @Test
    void recordsAndPullsEvents() {
        Order order = new Order(UUID.randomUUID());
        order.place();
        order.place();

        assertThat(order.domainEvents()).hasSize(2);

        var pulled = order.pullDomainEvents();
        assertThat(pulled).hasSize(2);
        assertThat(pulled.getFirst().eventType()).isEqualTo("OrderPlaced");
        assertThat(pulled.getFirst().eventId()).isNotNull();
        assertThat(pulled.getFirst().occurredOn()).isNotNull();
        assertThat(order.domainEvents()).isEmpty();
    }

    @Test
    void equalityIsIdentityBased() {
        UUID id = UUID.randomUUID();
        assertThat(new Order(id)).isEqualTo(new Order(id));
        assertThat(new Order(id)).isNotEqualTo(new Order(UUID.randomUUID()));
        assertThat(new Order(null)).isNotEqualTo(new Order(null));
    }

    @Test
    void nullEventRejected() {
        Order order = new Order(UUID.randomUUID());
        assertThatThrownBy(() -> order.registerEvent(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
