package br.com.rag_pgvector.dto;

import java.time.Instant;

import br.com.rag_pgvector.enums.DocumentTypeEnum;

/** Documento ingerido (arquitetura 05, seção 2.3). */
public record DocumentResponseDTO(
		Long id,
		Long clientId,
		String fileName,
		DocumentTypeEnum documentType,
		Instant createdAt,
		int totalChunks) {
}
