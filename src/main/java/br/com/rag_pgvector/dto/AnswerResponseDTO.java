package br.com.rag_pgvector.dto;

import java.util.List;

/** Resposta do {@code /ask} com as fontes numeradas (arquitetura 05, seção 2.5). */
public record AnswerResponseDTO(
		String answer,
		List<SourceResponseDTO> sources) {
}
