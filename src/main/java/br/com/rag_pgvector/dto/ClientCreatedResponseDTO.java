package br.com.rag_pgvector.dto;

/**
 * Resposta do cadastro. {@code apiKey} só aparece aqui; o banco guarda apenas o hash (ADR-008).
 */
public record ClientCreatedResponseDTO(Long id, String name, String apiKey) {
}
