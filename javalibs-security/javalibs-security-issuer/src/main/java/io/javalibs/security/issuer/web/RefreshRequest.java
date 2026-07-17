package io.javalibs.security.issuer.web;

/**
 * Request to rotate a refresh token and obtain a fresh token pair.
 */
public record RefreshRequest(String refreshToken) { }
