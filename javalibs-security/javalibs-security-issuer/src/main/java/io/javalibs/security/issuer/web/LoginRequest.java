package io.javalibs.security.issuer.web;

/**
 * Request to authenticate with username and password.
 */
public record LoginRequest(String username, String password) { }
