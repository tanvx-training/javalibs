package io.javalibs.security.issuer;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

/**
 * Hashes and verifies user passwords. Defaults to Spring Security's delegating encoder
 * (BCrypt, {@code {bcrypt}...} prefixed hashes) so the algorithm can be upgraded later
 * without invalidating stored hashes.
 */
public final class PasswordHasher {

    private final PasswordEncoder encoder;

    /**
     * Creates a PasswordHasher with the default delegating password encoder.
     */
    public PasswordHasher() {
        this(PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    /**
     * Creates a PasswordHasher with a custom password encoder.
     *
     * @param encoder the password encoder to use
     * @throws NullPointerException if encoder is null
     */
    public PasswordHasher(PasswordEncoder encoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder must not be null");
    }

    /**
     * Hashes a raw password.
     *
     * @param rawPassword the password to hash
     * @return the hashed password
     */
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * Verifies a raw password against a stored hash.
     *
     * @param rawPassword the raw password to verify
     * @param storedHash the stored hash to verify against
     * @return true if the password matches the hash, false otherwise
     */
    public boolean matches(String rawPassword, String storedHash) {
        return encoder.matches(rawPassword, storedHash);
    }
}
