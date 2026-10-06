package br.com.rag_pgvector.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pergunta do {@code /ask} (arquitetura 05, seção 2.5). Mesmos limites do {@link SearchRequestDTO}; sem
 * {@code topK} vale o padrão {@code app.rag.answer-top-k} (DP-04).
 */
public record AskRequestDTO(
		@NotBlank(message = "não deve estar em branco")
		@Size(max = SearchRequestDTO.MAX_QUESTION_LENGTH, message = "deve ter no máximo {max} caracteres")
		String question,
		@Min(value = 1, message = "deve ser no mínimo {value}")
		@Max(value = SearchRequestDTO.MAX_TOP_K, message = "deve ser no máximo {value}")
		Integer topK) {
}
