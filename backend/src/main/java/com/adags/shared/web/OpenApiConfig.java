package com.adags.shared.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI adagsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ADAGS Hospital Management System API")
                .version("v0"));
    }
}
