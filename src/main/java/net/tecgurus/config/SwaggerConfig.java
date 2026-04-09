package net.tecgurus.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(Arrays.asList(
                        new Server()
                                .url("https://contacto-electrico-api.tgconsulting.online")
                                .description("Production Server"),
                        new Server()
                                .url("http://localhost:8081")
                                .description("Local Development Server")
                ))
                .info(new Info()
                        .title("API de Ferretería")
                        .version("1.0")
                        .description("Documentación de la API de Ferretería")
                        .contact(new Contact()
                                .name("Soporte")
                                .email("soporte@ejemplo.com")
                        )
                );
    }
}