package br.com.rag_pgvector.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da OpenAI (ADR-006). A chave vem de {@code OPENAI_API_KEY}, nunca do repositório.
 * {@code chatModel} e {@code temperature} são do modelo que gera a resposta do {@code /ask}.
 */
@Validated
@ConfigurationProperties("app.openai")
public record OpenAiProperties(
		@NotBlank String apiKey,
		@NotBlank String embeddingModel,
		@NotBlank String chatModel,
		@NotNull @DecimalMin("0.0") @DecimalMax("2.0") Double temperature) {

	/** Não expõe a chave em logs nem em mensagens de erro. */
	@Override
	public String toString() {
		return "OpenAiProperties[apiKey=***, embeddingModel=" + embeddingModel + ", chatModel=" + chatModel
				+ ", temperature=" + temperature + "]";
	}

}
