package com.netpulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI netPulseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NetPulse Network Telemetry & Anomaly Detection API")
                        .version("0.1.0")
                        .description("REST API for managing synthetic network monitoring targets, probe telemetry, anomaly incidents, and chaos fault-injection rules.")
                        .contact(new Contact().name("NetPulse Engineering")));
    }
}
