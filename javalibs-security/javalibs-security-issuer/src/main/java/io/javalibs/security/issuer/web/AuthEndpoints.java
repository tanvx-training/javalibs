package io.javalibs.security.issuer.web;

import io.javalibs.security.issuer.AuthenticationService;
import io.javalibs.security.issuer.InvalidCredentialsException;
import io.javalibs.security.issuer.InvalidRefreshTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ready-to-use authentication endpoints: POST /auth/login, /auth/refresh, /auth/logout.
 * Auto-configured by javalibs-security when this module is on the classpath; the base path
 * is configurable via javalibs.security.issuer.endpoints.base-path. Remember to permit the
 * base path in javalibs.security.permit-all — login/refresh are anonymous by nature.
 */
@RestController
@RequestMapping("${javalibs.security.issuer.endpoints.base-path:/auth}")
public class AuthEndpoints {

    /** Machine readable error code for rejected logins. */
    public static final String ERR_INVALID_CREDENTIALS = "ERR_INVALID_CREDENTIALS";

    /** Machine readable error code for rejected refresh tokens. */
    public static final String ERR_INVALID_REFRESH_TOKEN = "ERR_INVALID_REFRESH_TOKEN";

    private final AuthenticationService authenticationService;

    /**
     * Constructs an AuthEndpoints handler with the given authentication service.
     *
     * @param authenticationService the service to authenticate requests; must not be null
     */
    public AuthEndpoints(AuthenticationService authenticationService) {
        this.authenticationService = Objects.requireNonNull(authenticationService);
    }

    /**
     * Authenticates with username/password and returns a fresh token pair.
     *
     * @param request login credentials
     * @return token response containing access and refresh tokens
     * @throws InvalidCredentialsException if credentials are invalid
     */
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        return TokenResponse.from(
                authenticationService.login(request.username(), request.password()));
    }

    /**
     * Rotates the refresh token and returns a fresh token pair.
     *
     * @param request the refresh token
     * @return token response containing new access and refresh tokens
     * @throws InvalidRefreshTokenException if refresh token is invalid or expired
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest request) {
        return TokenResponse.from(authenticationService.refresh(request.refreshToken()));
    }

    /**
     * Revokes the refresh token and blacklists the access token id when provided.
     *
     * @param request the refresh token to revoke and optional access token id to blacklist
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody LogoutRequest request) {
        authenticationService.logout(request.refreshToken(), request.accessTokenId());
    }

    /**
     * Handles InvalidCredentialsException and returns 401 with error details.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Map<String, Object>> onInvalidCredentials(InvalidCredentialsException ex) {
        return unauthorized(ERR_INVALID_CREDENTIALS, ex.getMessage());
    }

    /**
     * Handles InvalidRefreshTokenException and returns 401 with error details.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<Map<String, Object>> onInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return unauthorized(ERR_INVALID_REFRESH_TOKEN, ex.getMessage());
    }

    /**
     * Builds a 401 response with timestamp, status, error code and message.
     */
    private static ResponseEntity<Map<String, Object>> unauthorized(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
