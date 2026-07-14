package io.javalibs.ddd;

/**
 * Marker interface for value objects: immutable objects defined entirely by
 * their attributes, with no identity.
 *
 * <p>Implement with Java {@code record}s so equality, hashing and immutability
 * come for free:
 *
 * <pre>{@code
 * public record Money(BigDecimal amount, Currency currency) implements ValueObject {
 *     public Money {
 *         Objects.requireNonNull(amount);
 *         Objects.requireNonNull(currency);
 *     }
 * }
 * }</pre>
 */
public interface ValueObject {
}
