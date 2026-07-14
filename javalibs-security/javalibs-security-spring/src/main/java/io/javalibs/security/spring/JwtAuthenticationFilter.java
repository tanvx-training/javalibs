package io.javalibs.security.spring;

import io.javalibs.security.InvalidTokenException;
import io.javalibs.security.RevokedTokenException;
import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.TokenValidator;
import io.javalibs.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Servlet filter performing stateless JWT authentication.
 *
 * <p>The filter extracts the token from the {@code Authorization: Bearer &lt;token&gt;} header
 * and validates it with the configured {@link TokenValidator}:</p>
 *
 * <ul>
 *   <li><b>valid token</b> &rarr; a {@link UserContextAuthenticationToken} is stored in the
 *       {@link SecurityContextHolder} and the chain continues authenticated;</li>
 *   <li><b>invalid token</b> &rarr; the security context is cleared, the
 *       {@link InvalidTokenException} is stashed under {@link #AUTH_ERROR_ATTRIBUTE} and the
 *       chain continues <em>unauthenticated</em> — the authentication entry point is then
 *       responsible for producing the 401 response;</li>
 *   <li><b>no token</b> &rarr; the chain simply continues unauthenticated.</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Request attribute under which the {@link InvalidTokenException} of a failed validation is
     * stashed, so error handlers (e.g. {@link RestAuthenticationEntryPoint}) can produce a
     * precise message.
     */
    public static final String AUTH_ERROR_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenValidator tokenValidator;
    private final TokenBlacklist tokenBlacklist;

    /**
     * Creates the filter with the given validator and no revocation checking.
     *
     * @param tokenValidator the validator used to verify bearer tokens, never {@code null}
     */
    public JwtAuthenticationFilter(TokenValidator tokenValidator) {
        this(tokenValidator, null);
    }

    /**
     * Creates the filter with revocation checking: after signature validation the token's
     * {@code jti} claim is checked against the blacklist and revoked tokens are rejected
     * with a 401 like any other invalid token.
     *
     * @param tokenValidator the validator used to verify bearer tokens, never {@code null}
     * @param tokenBlacklist the revocation list, or {@code null} to skip revocation checks
     */
    public JwtAuthenticationFilter(TokenValidator tokenValidator, TokenBlacklist tokenBlacklist) {
        this.tokenValidator = Objects.requireNonNull(tokenValidator, "tokenValidator must not be null");
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                UserContext userContext = tokenValidator.validate(token);
                ensureNotRevoked(userContext);
                UserContextAuthenticationToken authentication =
                        new UserContextAuthenticationToken(userContext);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (InvalidTokenException ex) {
                log.debug("JWT validation failed for {} {}: {}",
                        request.getMethod(), request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ex);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Rejects the token when its {@code jti} claim is on the revocation list. Tokens without
     * a {@code jti} pass through (they cannot be revoked individually).
     *
     * @param userContext the validated user context
     * @throws RevokedTokenException when the token has been revoked
     */
    private void ensureNotRevoked(UserContext userContext) {
        if (tokenBlacklist == null) {
            return;
        }
        Object tokenId = userContext.attributes().get(TokenBlacklist.TOKEN_ID_ATTRIBUTE);
        if (tokenId != null && tokenBlacklist.isRevoked(tokenId.toString())) {
            throw new RevokedTokenException("Token has been revoked");
        }
    }

    /**
     * Extracts the bearer token from the {@code Authorization} header.
     *
     * @param request the current request
     * @return the raw token, or {@code null} when no bearer token is present
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
