package io.javalibs.security.issuer.web;

import io.javalibs.security.issuer.TokenPair;

import java.time.Instant;

/**
 * Successful token response containing access and refresh tokens.
 */
public record TokenResponse(String tokenType, String accessToken,
        Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {

    /**
     * Creates a TokenResponse from a TokenPair with token type set to "Bearer".
     */
    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse("Bearer", pair.accessToken(), pair.accessTokenExpiresAt(),
                pair.refreshToken(), pair.refreshTokenExpiresAt());
    }
}
