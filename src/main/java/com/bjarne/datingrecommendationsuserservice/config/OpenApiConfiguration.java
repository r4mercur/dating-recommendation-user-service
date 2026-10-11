package com.bjarne.datingrecommendationsuserservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document at /v3/api-docs and Swagger UI at /swagger-ui.html. Both are disabled by default and enabled in
 * the dev profile (springdoc.api-docs.enabled, springdoc.swagger-ui.enabled). In Swagger UI, "Authorize" takes the
 * access_token from POST /api/auth/login.
 */
@Configuration
public class OpenApiConfiguration {

    private static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dating Recommendations – User Service")
                        .version("v1")
                        .description("Profiles, recommendations, reactions, matches, chat, notifications and moderation. "
                                + "Errors are RFC 9457 problem details. WebSocket/STOMP at /ws is not part of this document, "
                                + "see docs/ROADMAP.md."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
