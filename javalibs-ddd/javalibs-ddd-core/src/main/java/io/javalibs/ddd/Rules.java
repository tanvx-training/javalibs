package io.javalibs.ddd;

/**
 * Entry point for enforcing {@link BusinessRule}s inside aggregates and domain
 * services.
 */
public final class Rules {

    private Rules() {
    }

    /**
     * Checks each rule in order and throws {@link BusinessRuleViolationException}
     * for the first violated one.
     */
    public static void check(BusinessRule... rules) {
        for (BusinessRule rule : rules) {
            if (rule.isViolated()) {
                throw new BusinessRuleViolationException(rule);
            }
        }
    }
}
