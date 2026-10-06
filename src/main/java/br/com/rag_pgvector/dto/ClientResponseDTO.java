package br.com.rag_pgvector.dto;

import java.time.Instant;

public record ClientResponseDTO(Long id, String name, Instant createdAt) {
}
