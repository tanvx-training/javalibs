package io.javalibs.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CacheKeysTest {

    @Test
    void joinsParts() {
        assertThat(CacheKeys.join("by-customer", "42")).isEqualTo("by-customer:42");
        assertThat(CacheKeys.join("single")).isEqualTo("single");
    }

    @Test
    void rejectsInvalidParts() {
        assertThatIllegalArgumentException().isThrownBy(CacheKeys::join);
        assertThatIllegalArgumentException().isThrownBy(() -> CacheKeys.join(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> CacheKeys.join("a b"));
        assertThatIllegalArgumentException().isThrownBy(() -> CacheKeys.join("a::b"));
        assertThatIllegalArgumentException().isThrownBy(() -> CacheKeys.join("a\nb"));
    }

    @Test
    void cacheSpecValidation() {
        assertThat(CacheSpec.ofTtl(Duration.ofMinutes(5)).ttl()).isEqualTo(Duration.ofMinutes(5));
        assertThatIllegalArgumentException().isThrownBy(() -> CacheSpec.ofTtl(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new CacheSpec(null, 0L));
    }
}
