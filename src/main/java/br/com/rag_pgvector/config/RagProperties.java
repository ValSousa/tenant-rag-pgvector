package br.com.rag_pgvector.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parâmetros do RAG. {@code embeddingDimension} tem de ser igual à da coluna {@code document_chunk.embedding}.
 * {@code chunkSize} e {@code chunkOverlap} são em caracteres. {@code defaultTopK} e
 * {@code maxTopK} são o padrão e o teto de resultados da busca; {@code answerTopK} é o padrão do
 * {@code /ask}, maior para perguntas que cruzam documentos.
 */
@Validated
@ConfigurationProperties("app.rag")
public record RagProperties(
		@Positive int embeddingDimension,
		@Positive int chunkSize,
		@PositiveOrZero int chunkOverlap,
		@Positive int defaultTopK,
		@Positive int answerTopK,
		@Positive int maxTopK) {
}
