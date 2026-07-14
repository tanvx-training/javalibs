package io.javalibs.security.spring;

import io.javalibs.security.UserContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Objects;

/**
 * Spring Security {@link org.springframework.security.core.Authentication} whose principal is a
 * javalibs {@link UserContext}.
 *
 * <p>Roles from the user context are exposed as {@code ROLE_}-prefixed
 * {@link GrantedAuthority authorities} (roles that already carry the prefix are not prefixed
 * twice), so both {@code hasRole("ADMIN")} and {@code hasAuthority("ROLE_ADMIN")} expressions
 * work as expected. Instances are always marked authenticated: they are only created after
 * successful token validation.</p>
 */
public class UserContextAuthenticationToken extends AbstractAuthenticationToken {

    private static final String ROLE_PREFIX = "ROLE_";

    private final transient UserContext userContext;

    /**
     * Creates an authenticated token for the given user context.
     *
     * @param userContext the validated user context, never {@code null}
     */
    public UserContextAuthenticationToken(UserContext userContext) {
        super(toAuthorities(Objects.requireNonNull(userContext, "userContext must not be null")));
        this.userContext = userContext;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> toAuthorities(UserContext userContext) {
        return userContext.roles().stream()
                .map(role -> role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * @return an empty string; the original token is never retained
     */
    @Override
    public Object getCredentials() {
        return "";
    }

    /**
     * {@inheritDoc}
     *
     * @return the validated {@link UserContext}
     */
    @Override
    public UserContext getPrincipal() {
        return userContext;
    }

    @Override
    public String getName() {
        return (userContext.username() != null) ? userContext.username() : userContext.userId();
    }
}
