package io.javalibs.security.spring;

import io.javalibs.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CurrentUserArgumentResolver}.
 */
class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unused")
    private void endpoint(@CurrentUser UserContext annotated, UserContext plain, String other) {
    }

    private MethodParameter parameter(int index) throws Exception {
        Method method = getClass().getDeclaredMethod("endpoint",
                UserContext.class, UserContext.class, String.class);
        return new MethodParameter(method, index);
    }

    @Test
    void supportsAnnotatedAndPlainUserContextParameters() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).isTrue();
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    }

    @Test
    void resolvesCurrentUser() throws Exception {
        UserContext user = UserContext.builder().userId("u1").build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UserContextAuthenticationToken(user));

        Object resolved = resolver.resolveArgument(parameter(0), null,
                new ServletWebRequest(new MockHttpServletRequest()), null);

        assertThat(resolved).isSameAs(user);
    }

    @Test
    void resolvesNullWhenUnauthenticated() throws Exception {
        Object resolved = resolver.resolveArgument(parameter(1), null,
                new ServletWebRequest(new MockHttpServletRequest()), null);

        assertThat(resolved).isNull();
    }
}
