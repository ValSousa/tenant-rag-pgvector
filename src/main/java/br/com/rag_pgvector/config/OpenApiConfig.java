package br.com.rag_pgvector.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da API e esquema de segurança {@code apiKey} para o botão Authorize do Swagger UI.
 */
@Configuration
@OpenAPIDefinition(
		info = @Info(title = "tenant-rag-pgvector", version = "v1",
				description = "RAG multi-tenant com PostgreSQL + pgvector"),
		security = @SecurityRequirement(name = "apiKey"))
@SecurityScheme(
		name = "apiKey",
		type = SecuritySchemeType.APIKEY,
		in = SecuritySchemeIn.HEADER,
		paramName = "X-API-Key")
public class OpenApiConfig {
}
