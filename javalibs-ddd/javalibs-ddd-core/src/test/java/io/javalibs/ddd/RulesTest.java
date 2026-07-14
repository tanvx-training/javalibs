package io.javalibs.ddd;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RulesTest {

    record MinimumAmount(int amount) implements BusinessRule {
        @Override
        public boolean isViolated() {
            return amount < 10;
        }

        @Override
        public String message() {
            return "Amount must be at least 10";
        }
    }

    @Test
    void passesWhenNotViolated() {
        assertThatCode(() -> Rules.check(new MinimumAmount(15))).doesNotThrowAnyException();
    }

    @Test
    void throwsWithRuleDetails() {
        assertThatThrownBy(() -> Rules.check(new MinimumAmount(5)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Amount must be at least 10")
                .extracting(e -> ((BusinessRuleViolationException) e).getRuleCode())
                .isEqualTo("MinimumAmount");
    }
}
