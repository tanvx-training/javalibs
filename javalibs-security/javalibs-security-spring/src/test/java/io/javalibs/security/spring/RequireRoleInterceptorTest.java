package io.javalibs.security.spring;

import io.javalibs.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link RequireRoleInterceptor}.
 */
class RequireRoleInterceptorTest {

    private final RequireRoleInterceptor interceptor = new RequireRoleInterceptor();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String... roles) {
        UserContext user = UserContext.builder().userId("u1").roles(roles).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UserContextAuthenticationToken(user));
    }

    private static HandlerMethod handler(Class<?> controller, String method) throws Exception {
        return new HandlerMethod(controller.getDeclaredConstructor().newInstance(),
                controller.getMethod(method));
    }

    @Test
    void nonHandlerMethodIsAllowed() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void methodWithoutAnnotationIsAllowed() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler(PlainController.class, "open")))
                .isTrue();
    }

    @Test
    void annotatedMethodAllowsUserWithRole() throws Exception {
        authenticate("ADMIN");

        assertThat(interceptor.preHandle(request, response,
                handler(PlainController.class, "adminOnly"))).isTrue();
    }

    @Test
    void annotatedMethodDeniesUserWithoutRole() throws Exception {
        authenticate("USER");

        HandlerMethod handler = handler(PlainController.class, "adminOnly");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void annotatedMethodDeniesUnauthenticatedCaller() throws Exception {
        HandlerMethod handler = handler(PlainController.class, "adminOnly");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anyOfAllowsAnyListedRole() throws Exception {
        authenticate("SUPPORT");

        assertThat(interceptor.preHandle(request, response,
                handler(PlainController.class, "adminOrSupport"))).isTrue();
    }

    @Test
    void allOfRequiresEveryListedRole() throws Exception {
        authenticate("ADMIN", "AUDITOR");
        assertThat(interceptor.preHandle(request, response,
                handler(PlainController.class, "adminAndAuditor"))).isTrue();

        authenticate("ADMIN");
        HandlerMethod handler = handler(PlainController.class, "adminAndAuditor");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void classLevelAnnotationApplies() throws Exception {
        authenticate("MANAGER");
        assertThat(interceptor.preHandle(request, response,
                handler(ClassLevelController.class, "managed"))).isTrue();

        authenticate("USER");
        HandlerMethod handler = handler(ClassLevelController.class, "managed");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void methodLevelAnnotationOverridesClassLevel() throws Exception {
        authenticate("AUDITOR");

        assertThat(interceptor.preHandle(request, response,
                handler(ClassLevelController.class, "auditorOnly"))).isTrue();
    }

    /** Test fixture: controller with method-level annotations. */
    public static class PlainController {
        public void open() {
        }

        @RequireRole("ADMIN")
        public void adminOnly() {
        }

        @RequireRole({"ADMIN", "SUPPORT"})
        public void adminOrSupport() {
        }

        @RequireRole(value = {"ADMIN", "AUDITOR"}, anyOf = false)
        public void adminAndAuditor() {
        }
    }

    /** Test fixture: controller with a class-level annotation. */
    @RequireRole("MANAGER")
    public static class ClassLevelController {
        public void managed() {
        }

        @RequireRole("AUDITOR")
        public void auditorOnly() {
        }
    }
}
