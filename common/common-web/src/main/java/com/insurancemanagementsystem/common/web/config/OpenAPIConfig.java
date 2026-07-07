package com.insurancemanagementsystem.common.web.config;

import io.swagger.v3.oas.models.Components;
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

@Configuration
public class OpenAPIConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Value("${openapi.title:#{null}}")
    private String title;

    @Value("${openapi.description:#{null}}")
    private String description;

    @Value("${openapi.version:0.0.1-SNAPSHOT}")
    private String apiVersion;

    @Value("${openapi.server-url:http://localhost:${server.port:8080}}")
    private String serverUrl;

    @Value("${openapi.server-description:Local development server}")
    private String serverDescription;

    @Bean
    public OpenAPI customOpenAPI() {
        final String resolvedTitle = (title != null) ? title : serviceName + " API";
        final String resolvedDescription = (description != null)
                ? description
                : "REST API for " + serviceName
                + ". This service is part of the Insurance Management System.";

        return new OpenAPI()
                .info(new Info()
                        .title(resolvedTitle)
                        .description(resolvedDescription)
                        .version(apiVersion)
                        .contact(new Contact()
                                .name("Development Team")
                                .email("dev@insurancemanagementsystem.com")
                                .url("https://insurancemanagementsystem.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://insurancemanagementsystem.com/license")))
                .addServersItem(new Server()
                        .url(serverUrl)
                        .description(serverDescription))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                    "JWT Authorization header using the Bearer scheme. "
                                  + "Example: \"Authorization: Bearer {token}\"")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
