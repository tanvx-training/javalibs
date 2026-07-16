package io.javalibs.security.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalibs.security.spring.RestAccessDeniedHandler;
import io.javalibs.security.spring.RestAuthenticationEntryPoint;
import io.javalibs.security.spring.UserContextJwtAuthenticationConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * OAuth2 Resource Server mode ({@code javalibs.security.mode=oauth2-resource-server}).
 *
 * <p>Token validation is delegated to Spring Security's resource-server support —
 * signatures are verified against the identity provider's JWKS, configured the
 * standard Spring way:</p>
 *
 * <pre>{@code
 * spring:
 *   security:
 *     oauth2:
 *       resourceserver:
 *         jwt:
 *           issuer-uri: https://keycloak.internal/realms/myrealm
 * }</pre>
 *
 * <p>Decoded tokens are converted to the javalibs {@code UserContext} by
 * {@link UserContextJwtAuthenticationConverter}, so {@code UserContextHolder},
 * {@code @CurrentUser} and {@code @RequireRole} keep working exactly as in the
 * default {@code jwt} mode. Switching a service between the two modes is a pure
 * configuration change.</p>
 */
@AutoConfiguration(
        beforeName = "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet."
                + "OAuth2ResourceServerAutoConfiguration",
        before = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class, Jwt.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "javalibs.security", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(prefix = "javalibs.security", name = "mode",
        havingValue = "oauth2-resource-server")
@EnableConfigurationProperties(SecurityProperties.class)
public class JavalibsOAuth2ResourceServerAutoConfiguration {

    /**
     * Maps decoded JWTs to {@code UserContextAuthenticationToken}s using the claim
     * names from {@code javalibs.security.jwt.*}.
     *
     * @param properties the bound security properties
     * @return the converter
     */
    @Bean
    @ConditionalOnMissingBean
    public UserContextJwtAuthenticationConverter javalibsUserContextJwtAuthenticationConverter(
            SecurityProperties properties) {
        SecurityProperties.Jwt jwt = properties.getJwt();
        return new UserContextJwtAuthenticationConverter(jwt.getRolesClaim(),
                jwt.getUsernameClaim(), jwt.getEmailClaim(), jwt.getTenantClaim());
    }

    /**
     * Registers the JSON 401 entry point (shared with jwt mode).
     *
     * @param objectMapper the application {@link ObjectMapper}, when available
     * @return the entry point
     */
    @Bean
    @ConditionalOnMissingBean
    public RestAuthenticationEntryPoint javalibsRestAuthenticationEntryPoint(
            ObjectProvider<ObjectMapper> objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /**
     * Registers the JSON 403 access denied handler (shared with jwt mode).
     *
     * @param objectMapper the application {@link ObjectMapper}, when available
     * @return the handler
     */
    @Bean
    @ConditionalOnMissingBean
    public RestAccessDeniedHandler javalibsRestAccessDeniedHandler(
            ObjectProvider<ObjectMapper> objectMapper) {
        return new RestAccessDeniedHandler(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /**
     * Stateless resource-server {@link SecurityFilterChain}. Relies on the
     * {@link JwtDecoder} that Spring Boot builds from
     * {@code spring.security.oauth2.resourceserver.jwt.*} being resolvable from the
     * {@link org.springframework.context.ApplicationContext} when {@link HttpSecurity#build()}
     * wires up {@code oauth2ResourceServer().jwt()} below; backs off when the application
     * defines its own chain.
     *
     * <p>This bean is intentionally gated by {@link JwtDecoderPropertyPresentCondition} —
     * an environment/property check mirroring Boot's own
     * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} /
     * {@code jwk-set-uri} / {@code public-key-location} conditions — rather than by
     * {@code @ConditionalOnBean(JwtDecoder.class)}. This configuration class is ordered
     * {@code beforeName} Boot's {@code OAuth2ResourceServerAutoConfiguration} so that this
     * {@link SecurityFilterChain} bean definition is registered first, causing Boot's own
     * default {@code jwtSecurityFilterChain} fallback (which does not know about
     * {@code UserContextJwtAuthenticationConverter}) to correctly back off via its own
     * {@code @ConditionalOnMissingBean(SecurityFilterChain.class)}. Because {@code beforeName}
     * makes this class run first, the {@code JwtDecoder} bean *definition* does not exist yet
     * at condition-evaluation time — only at actual bean instantiation time, once every
     * auto-configuration class has finished registering its bean definitions — so a
     * {@code @ConditionalOnBean(JwtDecoder.class)} guard here would always evaluate to false
     * and this chain would never be created (nor would Boot's own fallback, since it too is
     * gated on {@code @ConditionalOnBean(JwtDecoder.class)}). A property-based condition has
     * no such ordering dependency: the {@link org.springframework.core.env.Environment} is
     * fully populated before any configuration class's conditions are evaluated. When this
     * condition matches, Spring Security resolves the {@code JwtDecoder} lazily from the
     * context when this bean is instantiated, by which point Boot's
     * {@code JwtDecoderConfiguration} bean definition is already registered, so this works
     * correctly.</p>
     *
     * @param http                     the injected {@link HttpSecurity} prototype
     * @param properties               the bound security properties
     * @param authenticationConverter  the Jwt-to-UserContext converter
     * @param authenticationEntryPoint the 401 entry point
     * @param accessDeniedHandler      the 403 handler
     * @return the built filter chain
     * @throws Exception when {@link HttpSecurity} fails to build
     */
    @Bean
    @Conditional(JwtDecoderPropertyPresentCondition.class)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain javalibsOAuth2SecurityFilterChain(HttpSecurity http,
            SecurityProperties properties,
            UserContextJwtAuthenticationConverter authenticationConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    PathPatternRequestMatcher.Builder matchers = PathPatternRequestMatcher.withDefaults();
                    if (!properties.getPermitAll().isEmpty()) {
                        authorize.requestMatchers(properties.getPermitAll().stream()
                                .map(matchers::matcher)
                                .toArray(RequestMatcher[]::new)).permitAll();
                    }
                    authorize.requestMatchers(matchers.matcher(HttpMethod.OPTIONS, "/**")).permitAll();
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Matches when any of the {@code spring.security.oauth2.resourceserver.jwt.*}
     * properties that make Spring Boot build a {@link JwtDecoder} is present:
     * {@code issuer-uri}, {@code jwk-set-uri} or {@code public-key-location}. Used
     * instead of {@code @ConditionalOnBean(JwtDecoder.class)} so the match does not
     * depend on auto-configuration processing order (see
     * {@link #javalibsOAuth2SecurityFilterChain}).
     */
    static final class JwtDecoderPropertyPresentCondition extends AnyNestedCondition {

        JwtDecoderPropertyPresentCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
        static final class OnIssuerUri {
        }

        @ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
        static final class OnJwkSetUri {
        }

        @ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.public-key-location")
        static final class OnPublicKeyLocation {
        }
    }
}
