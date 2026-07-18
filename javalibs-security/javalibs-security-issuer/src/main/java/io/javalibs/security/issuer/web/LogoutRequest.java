package io.javalibs.security.issuer.web;

/**
 * Request to revoke the refresh token and optionally blacklist the access token.
 */
public record LogoutRequest(String refreshToken, String accessTokenId) { }
