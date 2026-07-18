package io.javalibs.security.issuer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAndVerifies() {
        String hash = hasher.hash("s3cret");
        assertThat(hash).startsWith("{bcrypt}");
        assertThat(hasher.matches("s3cret", hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
    }

    @Test
    void producesDifferentHashesForSamePassword() {
        assertThat(hasher.hash("s3cret")).isNotEqualTo(hasher.hash("s3cret"));
    }
}
