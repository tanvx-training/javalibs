package io.javalibs.cqrs.spring;

import java.util.List;
import java.util.UUID;

import io.javalibs.cqrs.Command;
import io.javalibs.cqrs.CommandHandler;
import io.javalibs.cqrs.CqrsException;
import io.javalibs.cqrs.DuplicateHandlerException;
import io.javalibs.cqrs.NoHandlerFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SpringCommandBus}.
 */
class SpringCommandBusTest {

    record CreateOrderCommand(String product) implements Command<UUID> {
    }

    record RenameOrderCommand(String name) implements Command<Void> {
    }

    record UnhandledCommand() implements Command<String> {
    }

    static class CreateOrderHandler implements CommandHandler<CreateOrderCommand, UUID> {
        static final UUID FIXED_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

        @Override
        public UUID handle(CreateOrderCommand command) {
            return FIXED_ID;
        }
    }

    static class RenameOrderHandler implements CommandHandler<RenameOrderCommand, Void> {
        String lastName;

        @Override
        public Void handle(RenameOrderCommand command) {
            this.lastName = command.name();
            return null;
        }
    }

    /** Generic base class used to verify resolution through a subclass hierarchy. */
    abstract static class AbstractHandler<C extends Command<R>, R> implements CommandHandler<C, R> {
    }

    static class SubclassedCreateOrderHandler extends AbstractHandler<CreateOrderCommand, UUID> {
        @Override
        public UUID handle(CreateOrderCommand command) {
            return CreateOrderHandler.FIXED_ID;
        }
    }

    @Test
    void dispatchRoutesToTheRightHandlerAndReturnsItsResult() {
        RenameOrderHandler renameHandler = new RenameOrderHandler();
        SpringCommandBus bus = new SpringCommandBus(List.of(new CreateOrderHandler(), renameHandler));

        UUID id = bus.dispatch(new CreateOrderCommand("book"));
        bus.dispatch(new RenameOrderCommand("new-name"));

        assertThat(id).isEqualTo(CreateOrderHandler.FIXED_ID);
        assertThat(renameHandler.lastName).isEqualTo("new-name");
    }

    @Test
    void dispatchWithoutMatchingHandlerThrowsNoHandlerFoundException() {
        SpringCommandBus bus = new SpringCommandBus(List.of(new CreateOrderHandler()));

        assertThatThrownBy(() -> bus.dispatch(new UnhandledCommand()))
                .isInstanceOf(NoHandlerFoundException.class)
                .hasMessageContaining(UnhandledCommand.class.getName());
    }

    @Test
    void twoHandlersForTheSameCommandThrowDuplicateHandlerException() {
        List<CommandHandler<?, ?>> handlers =
                List.of(new CreateOrderHandler(), new SubclassedCreateOrderHandler());

        assertThatThrownBy(() -> new SpringCommandBus(handlers))
                .isInstanceOf(DuplicateHandlerException.class)
                .hasMessageContaining(CreateOrderCommand.class.getName());
    }

    @Test
    void handlerDeclaredThroughSubclassHierarchyStillResolves() {
        SpringCommandBus bus = new SpringCommandBus(List.of(new SubclassedCreateOrderHandler()));

        assertThat(bus.dispatch(new CreateOrderCommand("book"))).isEqualTo(CreateOrderHandler.FIXED_ID);
    }

    @Test
    void anonymousClassHandlerStillResolves() {
        CommandHandler<CreateOrderCommand, UUID> anonymous =
                new CommandHandler<CreateOrderCommand, UUID>() {
                    @Override
                    public UUID handle(CreateOrderCommand command) {
                        return CreateOrderHandler.FIXED_ID;
                    }
                };
        SpringCommandBus bus = new SpringCommandBus(List.of(anonymous));

        assertThat(bus.dispatch(new CreateOrderCommand("book"))).isEqualTo(CreateOrderHandler.FIXED_ID);
    }

    @Test
    void lambdaHandlerIsRejectedWithExplanatoryException() {
        CommandHandler<CreateOrderCommand, UUID> lambda = command -> CreateOrderHandler.FIXED_ID;

        assertThatThrownBy(() -> new SpringCommandBus(List.of(lambda)))
                .isInstanceOf(CqrsException.class)
                .hasMessageContaining("Unable to resolve");
    }
}
