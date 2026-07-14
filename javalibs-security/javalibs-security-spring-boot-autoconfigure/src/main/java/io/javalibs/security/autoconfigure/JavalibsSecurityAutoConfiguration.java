package io.javalibs.security.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalibs.security.JwtTokenValidator;
import io.javalibs.security.JwtValidationConfig;
import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.TokenValidator;
import io.javalibs.security.spring.JwtAuthenticationFilter;
import io.javalibs.security.spring.RestAccessDeniedHandler;
import io.javalibs.security.spring.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for stateless JWT security on Spring Boot servlet applications.
 *
 * <p>Registers (each only when the application has not defined its own bean):</p>
 * <ul>
 *   <li>a {@link TokenValidator} built from {@link SecurityProperties} — the application fails
 *       fast at startup when neither {@code javalibs.security.jwt.secret} nor
 *       {@code javalibs.security.jwt.public-key} is configured;</li>
 *   <li>a {@link JwtAuthenticationFilter};</li>
 *   <li>REST-friendly {@link RestAuthenticationEntryPoint 401} and
 *       {@link RestAccessDeniedHandler 403} JSON error handlers;</li>
 *   <li>a stateless {@link SecurityFilterChain}: CSRF disabled, no HTTP session, the configured
 *       permit-all patterns and {@code OPTIONS} requests open, everything else authenticated.
 *       CORS is intentionally not configured here and is left to the web layer.</li>
 * </ul>
 *
 * <p>Disable everything with {@code javalibs.security.enabled=false}.</p>
 */
@AutoConfiguration(before = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "javalibs.security", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(prefix = "javalibs.security", name = "mode", havingValue = "jwt",
        matchIfMissing = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class JavalibsSecurityAutoConfiguration {

    /**
     * Builds the {@link TokenValidator} from the {@code javalibs.security.jwt.*} properties.
     *
     * @param properties the bound security properties
     * @return a {@link JwtTokenValidator}
     * @throws IllegalStateException when neither a secret nor a public key is configured
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenValidator javalibsTokenValidator(SecurityProperties properties) {
        SecurityProperties.Jwt jwt = properties.getJwt();
        boolean hasSecret = StringUtils.hasText(jwt.getSecret());
        boolean hasPublicKey = StringUtils.hasText(jwt.getPublicKey());
        if (!hasSecret && !hasPublicKey) {
            throw new IllegalStateException(
                    "javalibs.security.jwt.secret or javalibs.security.jwt.public-key must be "
                            + "configured to enable JWT authentication. Set one of the two properties, "
                            + "provide your own TokenValidator bean, or disable the module with "
                            + "javalibs.security.enabled=false.");
        }
        JwtValidationConfig config = JwtValidationConfig.builder()
                .hmacSecret(hasSecret ? jwt.getSecret() : null)
                .rsaPublicKeyPem(hasPublicKey ? jwt.getPublicKey() : null)
                .issuer(jwt.getIssuer())
                .audience(jwt.getAudience())
                .clockSkew(jwt.getClockSkew())
                .rolesClaim(jwt.getRolesClaim())
                .usernameClaim(jwt.getUsernameClaim())
                .emailClaim(jwt.getEmailClaim())
                .tenantClaim(jwt.getTenantClaim())
                .build();
        return new JwtTokenValidator(config);
    }

    /**
     * Registers the JWT authentication filter. When a {@link TokenBlacklist} bean exists (see
     * {@code javalibs.security.blacklist.mode}), revoked tokens are rejected after validation.
     *
     * @param tokenValidator the validator (auto-configured or application-provided)
     * @param tokenBlacklist the revocation list, when configured
     * @return the filter
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter javalibsJwtAuthenticationFilter(TokenValidator tokenValidator,
            ObjectProvider<TokenBlacklist> tokenBlacklist) {
        return new JwtAuthenticationFilter(tokenValidator, tokenBlacklist.getIfAvailable());
    }

    /**
     * Registers the JSON 401 entry point.
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
     * Registers the JSON 403 access denied handler.
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
     * Registers the stateless JWT {@link SecurityFilterChain}. Backs off entirely as soon as the
     * application declares any {@link SecurityFilterChain} bean of its own.
     *
     * @param http                    the injected {@link HttpSecurity} prototype
     * @param properties              the bound security properties
     * @param jwtAuthenticationFilter the JWT authentication filter
     * @param authenticationEntryPoint the 401 entry point
     * @param accessDeniedHandler     the 403 handler
     * @return the built filter chain
     * @throws Exception when {@link HttpSecurity} fails to build
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain javalibsSecurityFilterChain(HttpSecurity http,
            SecurityProperties properties,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    // Explicit PathPatternRequestMatchers keep this chain independent of the
                    // Spring MVC HandlerMappingIntrospector bean.
                    PathPatternRequestMatcher.Builder matchers = PathPatternRequestMatcher.withDefaults();
                    if (!properties.getPermitAll().isEmpty()) {
                        authorize.requestMatchers(properties.getPermitAll().stream()
                                .map(matchers::matcher)
                                .toArray(RequestMatcher[]::new)).permitAll();
                    }
                    authorize.requestMatchers(matchers.matcher(HttpMethod.OPTIONS, "/**")).permitAll();
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults());
        return http.build();
    }
}
