package io.javalibs.ddd;

/**
 * An explicit, named business rule that can be checked before a state change.
 *
 * <pre>{@code
 * public record OrderMustHaveItems(Order order) implements BusinessRule {
 *     public boolean isViolated() { return order.items().isEmpty(); }
 *     public String message() { return "An order must contain at least one item"; }
 * }
 *
 * // In the aggregate:
 * Rules.check(new OrderMustHaveItems(this));
 * }</pre>
 */
public interface BusinessRule {

    /** Returns {@code true} when the rule is broken in the current state. */
    boolean isViolated();

    /** Human-readable description of the violation. */
    String message();

    /** Stable rule code for API error payloads; defaults to the simple class name. */
    default String code() {
        return getClass().getSimpleName();
    }
}
