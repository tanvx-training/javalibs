package io.javalibs.authz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ScopeTest {

    @Test
    void globalScopeHasNoTypeAndNoId() {
        assertThat(Scope.GLOBAL.isGlobal()).isTrue();
        assertThat(Scope.GLOBAL.type()).isNull();
        assertThat(Scope.GLOBAL.id()).isNull();
    }

    @Test
    void ofCreatesResourceScope() {
        Scope scope = Scope.of("project", "42");
        assertThat(scope.isGlobal()).isFalse();
        assertThat(scope.type()).isEqualTo("project");
        assertThat(scope.id()).isEqualTo("42");
        assertThat(scope).isEqualTo(Scope.of("project", "42"));
        assertThat(scope).isNotEqualTo(Scope.of("project", "43"));
    }

    @Test
    void rejectsPartialScope() {
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.of("project", null));
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.of(null, "42"));
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.of(" ", "42"));
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.of("project", " "));
    }
}
