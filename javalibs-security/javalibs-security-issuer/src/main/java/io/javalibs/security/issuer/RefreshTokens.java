package io.javalibs.security.issuer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque refresh token utilities: 256-bit random url-safe tokens, stored only as their
 * SHA-256 hex hash so a database leak does not leak usable tokens.
 */
public final class RefreshTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokens() {
    }

    /**
     * Generates a 256-bit random token, base64url without padding (43 chars).
     *
     * @return a random refresh token in base64url format without padding
     */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Returns the lowercase SHA-256 hex digest of the token (64 chars).
     *
     * @param token the refresh token to hash
     * @return the SHA-256 hex digest (64 hexadecimal characters)
     */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JCA spec", ex);
        }
    }
}
