package br.com.rag_pgvector.exception;

/**
 * Recurso inexistente; vira 404 no {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

}
