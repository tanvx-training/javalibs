package io.javalibs.logging.spring;

import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Supplies the value of the {@code user_id} log field for a request.
 *
 * <p>Kept as a one-method interface so this module depends on neither
 * javalibs-security nor Spring Security. An application that keeps identity
 * somewhere else — a {@code UserContext}, a custom header, a tenant-qualified
 * id — registers its own bean and everything else keeps working.</p>
 */
@FunctionalInterface
public interface PrincipalResolver {

    /**
     * Reads {@link HttpServletRequest#getUserPrincipal()}, which servlet
     * containers and Spring Security both populate.
     */
    PrincipalResolver DEFAULT = request -> {
        Principal principal = request.getUserPrincipal();
        return (principal != null) ? principal.getName() : null;
    };

    /**
     * Resolves the identifier of the authenticated caller.
     *
     * @param request the current request
     * @return the user id, or {@code null} when the caller is anonymous
     */
    String resolve(HttpServletRequest request);
}
