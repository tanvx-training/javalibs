package io.javalibs.security.spring;

import io.javalibs.security.InMemoryTokenBlacklist;
import io.javalibs.security.RevokedTokenException;
import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterBlacklistTest {

    private final UserContext userWithJti = UserContext.builder()
            .userId("u-1")
            .attributes(Map.of(TokenBlacklist.TOKEN_ID_ATTRIBUTE, "jti-1"))
            .build();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer any-token");
        return request;
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        var blacklist = new InMemoryTokenBlacklist();
        blacklist.revoke("jti-1", Instant.now().plus(Duration.ofHours(1)));
        var filter = new JwtAuthenticationFilter(token -> userWithJti, blacklist);

        MockHttpServletRequest request = bearerRequest();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE))
                .isInstanceOf(RevokedTokenException.class);
    }

    @Test
    void nonRevokedTokenAuthenticates() throws Exception {
        var filter = new JwtAuthenticationFilter(token -> userWithJti, new InMemoryTokenBlacklist());

        filter.doFilter(bearerRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UserContextAuthenticationToken.class);
    }

    @Test
    void tokenWithoutJtiPassesWhenBlacklistConfigured() throws Exception {
        UserContext noJti = UserContext.builder().userId("u-2").build();
        var blacklist = new InMemoryTokenBlacklist();
        blacklist.revoke("jti-1", Instant.now().plus(Duration.ofHours(1)));
        var filter = new JwtAuthenticationFilter(token -> noJti, blacklist);

        filter.doFilter(bearerRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UserContextAuthenticationToken.class);
    }
}
