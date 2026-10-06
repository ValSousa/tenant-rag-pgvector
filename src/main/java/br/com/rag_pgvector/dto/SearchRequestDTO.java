package br.com.rag_pgvector.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pergunta da busca (arquitetura 05, seção 2.4). {@code topK} é opcional: sem ele vale o padrão
 * {@code app.rag.default-top-k} (DP-02). Os limites de {@code question} e {@code topK} valem também para o
 * {@link AskRequestDTO} (arquitetura 05, seção 2.5).
 */
public record SearchRequestDTO(
		@NotBlank(message = "não deve estar em branco")
		@Size(max = SearchRequestDTO.MAX_QUESTION_LENGTH, message = "deve ter no máximo {max} caracteres")
		String question,
		@Min(value = 1, message = "deve ser no mínimo {value}")
		@Max(value = SearchRequestDTO.MAX_TOP_K, message = "deve ser no máximo {value}")
		Integer topK) {

	/** Tamanho máximo da pergunta, em caracteres (busca e {@code /ask}). */
	public static final int MAX_QUESTION_LENGTH = 2000;

	/** Maior {@code topK} aceito na entrada (busca e {@code /ask}). */
	public static final int MAX_TOP_K = 20;

}
