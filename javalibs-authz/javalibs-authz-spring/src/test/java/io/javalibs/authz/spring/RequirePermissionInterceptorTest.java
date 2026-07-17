package io.javalibs.authz.spring;

import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class RequirePermissionInterceptorTest {

    static class TestController {
        @RequirePermission(value = "issue.read", scopeType = "project", scopeIdParam = "projectId")
        public void scoped() { }

        @RequirePermission("system.admin")
        public void global() { }

        public void unannotated() { }
    }

    private final RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor(
            new PermissionChecker(new PermissionEvaluator(
                    subject -> subject.equals(Subject.user("u1"))
                            ? List.of(new ResolvedGrant(Scope.of("project", "42"),
                                    Set.of("issue.read")))
                            : List.of(),
                    userId -> Set.of())));

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new io.javalibs.security.spring.UserContextAuthenticationToken(
                        UserContext.builder().userId("u1").build()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private HandlerMethod handler(String methodName) throws Exception {
        return new HandlerMethod(new TestController(),
                TestController.class.getMethod(methodName));
    }

    @Test
    void allowsWhenScopedPermissionGranted() throws Exception {
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("projectId", "42"));
        assertThat(interceptor.preHandle(request, response, handler("scoped"))).isTrue();
    }

    @Test
    void deniesWhenScopeDiffers() throws Exception {
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("projectId", "43"));
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> interceptor.preHandle(request, response, handler("scoped")));
    }

    @Test
    void deniesGlobalPermissionUserDoesNotHold() throws Exception {
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> interceptor.preHandle(request, response, handler("global")));
    }

    @Test
    void failsFastWhenScopeIdPathVariableMissing() throws Exception {
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of());
        assertThatIllegalStateException()
                .isThrownBy(() -> interceptor.preHandle(request, response, handler("scoped")))
                .withMessageContaining("projectId");
    }

    @Test
    void allowsUnannotatedHandlerAndNonHandlerMethod() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler("unannotated"))).isTrue();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}
