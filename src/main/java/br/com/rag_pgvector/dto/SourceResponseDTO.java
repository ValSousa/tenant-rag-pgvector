package br.com.rag_pgvector.dto;

import br.com.rag_pgvector.enums.DocumentTypeEnum;

/** Trecho usado na resposta. {@code ref} é o número {@code [n]} que o modelo cita no texto. */
public record SourceResponseDTO(
		int ref,
		Long documentId,
		String fileName,
		DocumentTypeEnum documentType,
		Integer chunkIndex) {
}
