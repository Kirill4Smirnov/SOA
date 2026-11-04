package ru.soa.booking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI flightBookingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Flight Booking REST API")
                .description("REST API Booking Service. Операции с рейсами проксируются в Flight Service по gRPC.")
                .version("1.0.0"));
    }
}

