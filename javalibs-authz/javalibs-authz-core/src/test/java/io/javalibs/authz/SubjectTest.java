package io.javalibs.authz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SubjectTest {

    @Test
    void createsUserAndGroupSubjects() {
        assertThat(Subject.user("u1")).isEqualTo(new Subject(SubjectType.USER, "u1"));
        assertThat(Subject.group("g1")).isEqualTo(new Subject(SubjectType.GROUP, "g1"));
    }

    @Test
    void rejectsBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() -> Subject.user(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> Subject.group(null));
    }
}
