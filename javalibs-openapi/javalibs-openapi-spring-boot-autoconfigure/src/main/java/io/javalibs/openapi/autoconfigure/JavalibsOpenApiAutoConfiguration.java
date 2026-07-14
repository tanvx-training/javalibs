package io.javalibs.openapi.autoconfigure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Standardizes the OpenAPI document across services:
 *
 * <ul>
 *   <li>API info (title, version, description, contact, servers) bound from
 *       {@code javalibs.openapi.*};</li>
 *   <li>the shared {@code bearerAuth} HTTP bearer (JWT) security scheme applied
 *       globally, so Swagger UI shows the Authorize button and clients know to
 *       send {@code Authorization: Bearer};</li>
 *   <li>via {@link GlobalErrorResponsesCustomizer}, the platform-wide error
 *       responses documented on every operation.</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "javalibs.openapi", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
public class JavalibsOpenApiAutoConfiguration {

    /**
     * Builds the base OpenAPI document from properties.
     *
     * @param properties  the bound OpenAPI properties
     * @param environment used to default the title from spring.application.name
     * @return the document
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAPI javalibsOpenApi(OpenApiProperties properties, Environment environment) {
        String applicationName = environment.getProperty("spring.application.name", "service");
        Info info = new Info()
                .title(StringUtils.hasText(properties.getTitle())
                        ? properties.getTitle() : applicationName + " API")
                .version(properties.getVersion())
                .description(properties.getDescription());
        OpenApiProperties.Contact contact = properties.getContact();
        if (StringUtils.hasText(contact.getName()) || StringUtils.hasText(contact.getEmail())) {
            info.contact(new Contact()
                    .name(contact.getName())
                    .email(contact.getEmail())
                    .url(contact.getUrl()));
        }

        OpenAPI openApi = new OpenAPI().info(info).components(new Components());
        properties.getServers().forEach(url -> openApi.addServersItem(new Server().url(url)));

        if (properties.getSecurity().isEnabled()) {
            String schemeName = properties.getSecurity().getSchemeName();
            openApi.getComponents().addSecuritySchemes(schemeName, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT access token (Authorization: Bearer <token>)"));
            openApi.addSecurityItem(new SecurityRequirement().addList(schemeName));
        }
        return openApi;
    }

    /**
     * Registers the global error responses customizer when springdoc's customizer
     * SPI is present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GlobalOpenApiCustomizer.class)
    @ConditionalOnProperty(prefix = "javalibs.openapi.global-responses", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class GlobalResponsesConfiguration {

        @Bean
        @ConditionalOnMissingBean
        GlobalErrorResponsesCustomizer javalibsGlobalErrorResponsesCustomizer() {
            return new GlobalErrorResponsesCustomizer();
        }
    }
}
