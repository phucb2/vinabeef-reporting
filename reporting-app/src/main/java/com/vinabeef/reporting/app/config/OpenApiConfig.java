package com.vinabeef.reporting.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API description served by springdoc at /v3/api-docs, with Swagger UI at /swagger-ui.html.
 * Every endpoint needs a JWT, so the document declares one global bearer scheme: Swagger UI's
 * "Authorize" button then sends it on every call (locally: paste the output of scripts/dev-token.sh).
 * Exposure is controlled by springdoc.api-docs.enabled / springdoc.swagger-ui.enabled (on in the local profile).
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI reportingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VinaBeef Reporting API")
                        .version("v1")
                        .description("""
                                Report export (FR-11), NADCS sync (FR-12) and dashboard KPIs (FR-13).

                                **Time range**: pass either `period` (`2026`, `2026-H1`, `2026-Q2`, `2026-03`, \
                                `2026-W05`, `2026-03-15`) or both `from` and `to` (ISO dates, inclusive). \
                                Day, month and quarter boundaries are cut in farm local time (Asia/Ho_Chi_Minh).

                                **Roles** come from the JWT `roles` claim: MANAGER / EXEC read reports, ADMIN triggers syncs.
                                Errors are RFC 7807 problem details."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT with a `roles` claim, e.g. [\"MANAGER\"]. Locally: `scripts/dev-token.sh MANAGER`.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
