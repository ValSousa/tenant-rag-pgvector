package br.com.rag_pgvector.security;

/**
 * Cliente autenticado pela própria chave de API.
 */
public record ClientPrincipal(long clientId) {
}
