package br.com.rag_pgvector.dto;

import java.util.List;

/** Resultado da busca, do chunk mais parecido para o menos parecido (arquitetura 05, seção 2.4). */
public record SearchResponseDTO(
		Long clientId,
		String question,
		List<SearchResultDTO> results) {
}
