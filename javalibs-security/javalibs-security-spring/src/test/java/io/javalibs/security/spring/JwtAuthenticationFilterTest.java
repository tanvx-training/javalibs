package io.javalibs.security.spring;

import io.javalibs.security.InvalidTokenException;
import io.javalibs.security.TokenValidator;
import io.javalibs.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JwtAuthenticationFilter}.
 */
class JwtAuthenticationFilterTest {

    private static final UserContext USER = UserContext.builder()
            .userId("u1")
            .username("jdoe")
            .roles("ADMIN")
            .build();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenSetsAuthenticationAndContinuesChain() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(token -> USER);
        request.addHeader("Authorization", "Bearer valid-token");

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isInstanceOf(UserContextAuthenticationToken.class);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(((UserContextAuthenticationToken) authentication).getPrincipal().userId())
                .isEqualTo("u1");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        assertThat(chain.getRequest()).as("chain must continue").isNotNull();
    }

    @Test
    void invalidTokenClearsContextStashesErrorAndContinuesChain() throws Exception {
        InvalidTokenException failure = new InvalidTokenException("Token is invalid: bad signature");
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(token -> {
            throw failure;
        });
        request.addHeader("Authorization", "Bearer tampered-token");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE))
                .isSameAs(failure);
        assertThat(chain.getRequest()).as("chain must continue unauthenticated").isNotNull();
        assertThat(response.getStatus()).as("filter must not write the 401 itself").isEqualTo(200);
    }

    @Test
    void missingAuthorizationHeaderContinuesUnauthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(token -> USER);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void nonBearerAuthorizationHeaderIsIgnored() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(token -> USER);
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void bearerPrefixIsCaseInsensitive() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(token -> USER);
        request.addHeader("Authorization", "bearer some-token");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UserContextAuthenticationToken.class);
    }
}
