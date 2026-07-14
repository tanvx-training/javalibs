package io.javalibs.ddd;

/**
 * Thrown when a {@link BusinessRule} is violated. Carries the broken rule so the
 * API layer can translate it into a structured error response.
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final transient BusinessRule rule;

    public BusinessRuleViolationException(BusinessRule rule) {
        super(rule.message());
        this.rule = rule;
    }

    public BusinessRule getRule() {
        return rule;
    }

    /** Stable rule code (see {@link BusinessRule#code()}). */
    public String getRuleCode() {
        return rule.code();
    }
}
