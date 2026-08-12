package com.idp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 contract for the platform.
 *
 * <p>The spec is generated from the live controllers (springdoc), so it can
 * never drift from the code — every registered service's contract is the actual
 * implementation. Authorize in Swagger UI with a Keycloak bearer token, which
 * the resource server maps to the internal RBAC/ABAC principal.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI idpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IDP Platform API")
                        .version("1.0.0")
                        .description("Internal Developer Platform: service catalog with ownership and dependency "
                                + "metadata, RBAC/ABAC authorization, feature-flag canary evaluation, audit trail, "
                                + "scaffolding and GitHub integration. Every endpoint is authorized unless listed "
                                + "as explicitly public.")
                        .contact(new Contact().name("Platform Team").email("platform@company.internal")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak access token for the idp-realm issued by the OIDC provider")));
    }
}