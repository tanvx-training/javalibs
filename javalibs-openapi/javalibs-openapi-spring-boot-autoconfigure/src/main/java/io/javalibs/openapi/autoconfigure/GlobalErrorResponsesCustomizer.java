package io.javalibs.openapi.autoconfigure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Documents the platform-wide error contract on every operation: the standard
 * javalibs-web {@code ErrorResponse} JSON body for 400, 401, 403, 404, 409 and
 * 500. Frontend teams see the exact failure shape (including the
 * {@code ERR-<DOMAIN>-<SEQ>} business codes) without each service documenting
 * it by hand. Responses already declared by an operation are left untouched.
 */
public class GlobalErrorResponsesCustomizer implements GlobalOpenApiCustomizer {

    /** Name of the shared error schema registered in the components section. */
    public static final String ERROR_SCHEMA_NAME = "ErrorResponse";

    private static final Map<String, String> GLOBAL_RESPONSES = Map.of(
            "400", "Bad request — validation failed or malformed input",
            "401", "Unauthorized — missing, invalid, expired or revoked token",
            "403", "Forbidden — authenticated but lacking the required role",
            "404", "Not found — resource does not exist",
            "409", "Conflict — state conflict (duplicate, concurrent update)",
            "500", "Internal server error — unexpected failure, safe generic message");

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        openApi.getComponents().addSchemas(ERROR_SCHEMA_NAME, errorResponseSchema());

        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    Map<String, String> ordered = new LinkedHashMap<>(GLOBAL_RESPONSES);
                    ordered.forEach((status, description) -> {
                        if (operation.getResponses() != null
                                && !operation.getResponses().containsKey(status)) {
                            operation.getResponses().addApiResponse(status,
                                    errorApiResponse(description));
                        }
                    });
                }));
    }

    private ApiResponse errorApiResponse(String description) {
        Schema<?> reference = new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA_NAME);
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(reference)));
    }

    /** Mirrors io.javalibs.web.ErrorResponse. */
    private Schema<?> errorResponseSchema() {
        ObjectSchema fieldViolation = new ObjectSchema();
        fieldViolation.addProperty("field", new StringSchema().example("email"));
        fieldViolation.addProperty("message", new StringSchema().example("must be a well-formed email address"));

        ObjectSchema schema = new ObjectSchema();
        schema.description("Standard javalibs error payload");
        schema.addProperty("timestamp", new StringSchema().format("date-time"));
        schema.addProperty("status", new IntegerSchema().example(400));
        schema.addProperty("code", new StringSchema()
                .description("Machine-readable error code (ERR_* or business ERR-<DOMAIN>-<SEQ>)")
                .example("ERR-USER-001"));
        schema.addProperty("message", new StringSchema().example("User with id 42 not found"));
        schema.addProperty("path", new StringSchema().example("/api/users/42"));
        schema.addProperty("traceId", new StringSchema().nullable(true));
        schema.addProperty("fieldErrors", new ArraySchema().items(fieldViolation).nullable(true));
        return schema;
    }
}
