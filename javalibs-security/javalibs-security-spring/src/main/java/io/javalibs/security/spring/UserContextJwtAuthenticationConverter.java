package io.javalibs.security.spring;

import io.javalibs.security.UserContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bridges Spring Security's OAuth2 Resource Server support to the javalibs
 * security model: converts a decoded {@link Jwt} (validated by Spring against
 * the IAM's JWKS, e.g. Keycloak) into a {@link UserContextAuthenticationToken}.
 *
 * <p>This keeps the whole javalibs API — {@code UserContextHolder},
 * {@code @CurrentUser}, {@code @RequireRole} — working identically whether the
 * service validates tokens itself ({@code javalibs.security.mode=jwt}) or
 * delegates to an OIDC provider
 * ({@code javalibs.security.mode=oauth2-resource-server}).</p>
 */
public class UserContextJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Set<String> REGISTERED_CLAIMS = Set.of(
            JwtClaimNames.SUB, JwtClaimNames.ISS, JwtClaimNames.AUD,
            JwtClaimNames.EXP, JwtClaimNames.IAT, JwtClaimNames.NBF);

    private final String rolesClaim;
    private final String usernameClaim;
    private final String emailClaim;
    private final String tenantClaim;

    /**
     * Creates the converter with the claim names to map.
     *
     * @param rolesClaim    claim carrying the roles (JSON array or delimited string)
     * @param usernameClaim claim carrying the username
     * @param emailClaim    claim carrying the email
     * @param tenantClaim   claim carrying the tenant id
     */
    public UserContextJwtAuthenticationConverter(String rolesClaim, String usernameClaim,
            String emailClaim, String tenantClaim) {
        this.rolesClaim = rolesClaim;
        this.usernameClaim = usernameClaim;
        this.emailClaim = emailClaim;
        this.tenantClaim = tenantClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserContext userContext = UserContext.builder()
                .userId(jwt.getSubject())
                .username(jwt.getClaimAsString(usernameClaim))
                .email(jwt.getClaimAsString(emailClaim))
                .roles(extractRoles(jwt.getClaim(rolesClaim)))
                .tenantId(jwt.getClaimAsString(tenantClaim))
                .attributes(extractAttributes(jwt))
                .build();
        return new UserContextAuthenticationToken(userContext);
    }

    private Set<String> extractRoles(Object rolesClaimValue) {
        Set<String> roles = new LinkedHashSet<>();
        if (rolesClaimValue instanceof Collection<?> collection) {
            for (Object role : collection) {
                if (role != null && !role.toString().isBlank()) {
                    roles.add(role.toString());
                }
            }
        } else if (rolesClaimValue instanceof String rolesString) {
            for (String role : rolesString.split("[,\\s]+")) {
                if (!role.isBlank()) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    private Map<String, Object> extractAttributes(Jwt jwt) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : jwt.getClaims().entrySet()) {
            String key = entry.getKey();
            if (entry.getValue() == null || REGISTERED_CLAIMS.contains(key)) {
                continue;
            }
            if (key.equals(rolesClaim) || key.equals(usernameClaim)
                    || key.equals(emailClaim) || key.equals(tenantClaim)) {
                continue;
            }
            attributes.put(key, entry.getValue());
        }
        return attributes;
    }
}
