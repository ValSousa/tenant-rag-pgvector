package br.com.rag_pgvector.exception;

/**
 * PDF ilegível ou sem texto extraível; vira 422 no {@link GlobalExceptionHandler}.
 * A mensagem é mostrada ao usuário, então é escrita em português e sem detalhes internos.
 */
public class InvalidDocumentException extends RuntimeException {

	public InvalidDocumentException(String message) {
		super(message);
	}

	public InvalidDocumentException(String message, Throwable cause) {
		super(message, cause);
	}

}
