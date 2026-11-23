package br.com.luiz.workflow.adapters.in.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados da documentação OpenAPI gerada pelo springdoc (título e descrição no Swagger UI). */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI workflowOpenApi() {
        return new OpenAPI().info(new Info()
            .title("WorkFlow Crédito - API REST")
            .version("0.1.0")
            .description("Propostas de crédito: criação, consulta, listagem por cursor e transições de status. "
                + "Erros no formato RFC 9457 (application/problem+json); valores monetários como texto."));
    }
}
