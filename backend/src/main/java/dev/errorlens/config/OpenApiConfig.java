package dev.errorlens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI errorLensOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ErrorLens API")
                        .version("v1")
                        .description("Execute. Trace. Investigate. Understand. — API de laboratório para simulação, "
                                + "rastreamento e diagnóstico de erros em aplicações Java. Use POST /api/v1/auth/login "
                                + "para obter um token (Bearer) ou HTTP Basic com as identidades de laboratório.")
                        .license(new License().name("Uso em laboratório local")))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").description("Token emitido por /api/v1/auth/login"))
                        .addSecuritySchemes("basic", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("basic").description("Identidades locais de laboratório")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                .addSecurityItem(new SecurityRequirement().addList("basic"));
    }
}
