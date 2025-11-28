package com.ella.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    @Bean
    public OpenAPI ellaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ELLA Finanças API")
                        .description("Backend da plataforma ELLA – sua parceira inteligente de organização financeira.")
                        .version("v1")
                        .contact(new Contact()
                                .name("ELLA")
                                .email("support@ella.app") // pode trocar depois
                        )
                )
                // Configuração global do esquema de segurança (JWT Bearer)
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                )
                // Aplica o esquema de segurança em todos os endpoints por padrão
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
