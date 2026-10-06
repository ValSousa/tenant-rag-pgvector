package br.com.rag_pgvector.exception;

/**
 * Falha do provedor de IA (indisponível, chave inválida, limite de uso, dimensão errada); vira 503 no
 * {@link GlobalExceptionHandler}. A mensagem vai só para o log, nunca para o corpo da resposta.
 */
public class AiProviderException extends RuntimeException {

	public AiProviderException(String message) {
		super(message);
	}

	public AiProviderException(String message, Throwable cause) {
		super(message, cause);
	}

}
