package com.deviceapi.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI devicesApiOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Devices API")
                .description("REST API for persisting and managing device resources")
                .version("v1"));
    }
}
