package io.javalibs.security.issuer;

import java.time.Instant;

/**
 * A freshly issued access + refresh token pair, as returned by
 * {@link AuthenticationService#login(String, String)} and
 * {@link AuthenticationService#refresh(String)}.
 *
 * @param accessToken the signed JWT access token
 * @param accessTokenExpiresAt when the access token expires
 * @param refreshToken the raw (unhashed) refresh token; store only its hash, hand this
 *     value to the caller
 * @param refreshTokenExpiresAt when the refresh token expires
 */
public record TokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}
