package com.example.eventmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OPENAPI / SWAGGER CONFIGURATION
 * ==============================
 * Generates the interactive API documentation served at `/swagger-ui.html`.
 *
 * The `Bearer` security scheme is declared once here and attached to the API as
 * a whole, so Swagger UI shows the “Authorize” button and every secured
 * endpoint can be exercised straight from the browser with a real token.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI eventforgeOpenApi(
        @Value("${server.port:8080}") int port,
        @Value("${springdoc.server-url:http://localhost:${server.port}}") String configuredUrl) {

        String url = configuredUrl.replace("${server.port}", String.valueOf(port));

        return new OpenAPI()
            .info(new Info()
                .title("Eventforge API")
                .version("1.0.0")
                .description("""
                    REST API for the Eventforge event management platform.

                    **Authentication:** `POST /api/v1/auth/login` returns a JWT. Send it
                    as `Authorization: Bearer <token>` on every subsequent request.

                    **Response envelope:**
                    - success → `{ "success": true, "message": "...", "data": {...} }`
                    - failure → `{ "success": false, "message": "...", "errorCode": "..." }`

                    **Error codes:** `EVENT_FULL`, `DUPLICATE_REGISTRATION`,
                    `CONCURRENT_MODIFICATION`, `NOTHING_TO_UNDO`, `FORBIDDEN`, ...
                    """)
                .contact(new Contact()
                    .name("Eventforge Engineering"))
                .license(new License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(new Server().url(url).description("Local development")))
            .addSecurityItem(new SecurityRequirement().addList(SCHEME))
            .schemaRequirement(SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste the JWT returned by `/api/v1/auth/login`"));
    }
}
