package br.com.rag_pgvector.ingestion;

/**
 * Trecho de um documento: {@code index} é o {@code chunk_index}, a partir de 0, na ordem do texto.
 */
public record Chunk(int index, String content) {
}
