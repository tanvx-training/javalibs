package io.javalibs.openapi.autoconfigure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsOpenApiAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JavalibsOpenApiAutoConfiguration.class));

    @Test
    void buildsDocumentWithDefaultsAndSecurityScheme() {
        runner.withPropertyValues("spring.application.name=orders-service")
                .run(context -> {
                    OpenAPI openApi = context.getBean(OpenAPI.class);
                    assertThat(openApi.getInfo().getTitle()).isEqualTo("orders-service API");
                    assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
                    SecurityScheme scheme =
                            openApi.getComponents().getSecuritySchemes().get("bearerAuth");
                    assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
                    assertThat(scheme.getScheme()).isEqualTo("bearer");
                    assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
                    assertThat(openApi.getSecurity())
                            .anySatisfy(req -> assertThat(req).containsKey("bearerAuth"));
                });
    }

    @Test
    void propertiesOverrideInfoAndDisableSecurity() {
        runner.withPropertyValues(
                        "javalibs.openapi.title=Orders API",
                        "javalibs.openapi.version=v2",
                        "javalibs.openapi.servers[0]=https://api.example.com",
                        "javalibs.openapi.contact.name=Payments Team",
                        "javalibs.openapi.contact.email=payments@example.com",
                        "javalibs.openapi.security.enabled=false")
                .run(context -> {
                    OpenAPI openApi = context.getBean(OpenAPI.class);
                    assertThat(openApi.getInfo().getTitle()).isEqualTo("Orders API");
                    assertThat(openApi.getInfo().getContact().getEmail())
                            .isEqualTo("payments@example.com");
                    assertThat(openApi.getServers()).extracting("url")
                            .containsExactly("https://api.example.com");
                    assertThat(openApi.getComponents().getSecuritySchemes()).isNullOrEmpty();
                });
    }

    @Test
    void disabledFlagBacksOff() {
        runner.withPropertyValues("javalibs.openapi.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OpenAPI.class));
    }

    @Test
    void globalResponsesCustomizerRegisteredAndGated() {
        runner.run(context ->
                assertThat(context).hasSingleBean(GlobalErrorResponsesCustomizer.class));
        runner.withPropertyValues("javalibs.openapi.global-responses.enabled=false")
                .run(context ->
                        assertThat(context).doesNotHaveBean(GlobalErrorResponsesCustomizer.class));
    }

    @Test
    void customizerAddsErrorResponsesWithoutOverridingExisting() {
        var customizer = new GlobalErrorResponsesCustomizer();

        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("OK"))
                .addApiResponse("404", new ApiResponse().description("custom not-found")));
        OpenAPI openApi = new OpenAPI()
                .paths(new Paths().addPathItem("/orders", new PathItem().get(operation)));

        customizer.customise(openApi);

        assertThat(operation.getResponses().keySet())
                .contains("200", "400", "401", "403", "404", "409", "500");
        // Existing responses stay untouched.
        assertThat(operation.getResponses().get("404").getDescription())
                .isEqualTo("custom not-found");
        assertThat(operation.getResponses().get("500").getContent()
                .get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(openApi.getComponents().getSchemas())
                .containsKey(GlobalErrorResponsesCustomizer.ERROR_SCHEMA_NAME);
    }
}
