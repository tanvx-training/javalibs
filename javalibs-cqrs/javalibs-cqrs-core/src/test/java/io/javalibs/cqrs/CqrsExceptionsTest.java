package io.javalibs.cqrs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the CQRS exception hierarchy.
 */
class CqrsExceptionsTest {

    private record SampleCommand(String value) implements Command<String> {
    }

    @Test
    void cqrsExceptionCarriesMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        CqrsException exception = new CqrsException("something failed", cause);

        assertThat(exception).hasMessage("something failed").hasCause(cause);
        assertThat(new CqrsException("plain")).hasMessage("plain").hasNoCause();
    }

    @Test
    void noHandlerFoundExceptionCarriesMessageTypeName() {
        NoHandlerFoundException exception = new NoHandlerFoundException(SampleCommand.class);

        assertThat(exception)
                .isInstanceOf(CqrsException.class)
                .hasMessageContaining(SampleCommand.class.getName());
        assertThat(exception.getMessageType()).isEqualTo(SampleCommand.class);
    }

    @Test
    void duplicateHandlerExceptionCarriesMessageTypeAndHandlerNames() {
        DuplicateHandlerException exception =
                new DuplicateHandlerException(SampleCommand.class, String.class, Integer.class);

        assertThat(exception)
                .isInstanceOf(CqrsException.class)
                .hasMessageContaining(SampleCommand.class.getName())
                .hasMessageContaining(String.class.getName())
                .hasMessageContaining(Integer.class.getName());
        assertThat(exception.getMessageType()).isEqualTo(SampleCommand.class);
    }
}
