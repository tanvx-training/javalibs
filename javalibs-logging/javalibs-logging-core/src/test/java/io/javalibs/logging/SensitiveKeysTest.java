package io.javalibs.logging;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveKeysTest {

    @Test
    void matchesDefaultKeysRegardlessOfCaseAndSeparators() {
        SensitiveKeys keys = SensitiveKeys.defaults();

        assertThat(keys.isSensitive("password")).isTrue();
        assertThat(keys.isSensitive("PASSWORD")).isTrue();
        assertThat(keys.isSensitive("Pass_Word")).isTrue();
        assertThat(keys.isSensitive("pass-word")).isTrue();
        assertThat(keys.isSensitive("access_token")).isTrue();
        assertThat(keys.isSensitive("accessToken")).isTrue();
        assertThat(keys.isSensitive("API KEY")).isTrue();
    }

    @Test
    void matchesCommonSecretCarryingNamesSeenInTheWild() {
        SensitiveKeys keys = SensitiveKeys.defaults();

        assertThat(keys.isSensitive("Cookie")).isTrue();
        assertThat(keys.isSensitive("Set-Cookie")).isTrue();
        assertThat(keys.isSensitive("X-API-Key")).isTrue();
        assertThat(keys.isSensitive("sessionId")).isTrue();
        assertThat(keys.isSensitive("creditCard")).isTrue();
        assertThat(keys.isSensitive("apiSecret")).isTrue();
    }

    @Test
    void leavesOrdinaryKeysAlone() {
        SensitiveKeys keys = SensitiveKeys.defaults();

        assertThat(keys.isSensitive("email")).isFalse();
        assertThat(keys.isSensitive("amount")).isFalse();
        assertThat(keys.isSensitive("passenger")).isFalse();
        assertThat(keys.isSensitive("")).isFalse();
        assertThat(keys.isSensitive(null)).isFalse();
    }

    @Test
    void additionalKeysAddToDefaultsInsteadOfReplacingThem() {
        SensitiveKeys keys = SensitiveKeys.withAdditional(List.of("national-id"));

        assertThat(keys.isSensitive("nationalId")).isTrue();
        assertThat(keys.isSensitive("password")).isTrue();
    }

    @Test
    void noneMatchesNothingSoMaskingCanBeTurnedOff() {
        SensitiveKeys keys = SensitiveKeys.none();

        assertThat(keys.isSensitive("password")).isFalse();
        assertThat(keys.isSensitive("token")).isFalse();
    }
}
