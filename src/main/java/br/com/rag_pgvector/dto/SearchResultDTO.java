package br.com.rag_pgvector.dto;

import java.util.UUID;

import br.com.rag_pgvector.enums.DocumentTypeEnum;

/**
 * Um chunk encontrado na busca (arquitetura 05, seção 2.4). {@code chunkId} é o {@code embedding_id};
 * {@code score} vai de 0 a 1, em que 1 é idêntico (arquitetura 04, seção 5.2).
 */
public record SearchResultDTO(
		UUID chunkId,
		Long documentId,
		String fileName,
		DocumentTypeEnum documentType,
		Integer chunkIndex,
		String content,
		double score) {
}
