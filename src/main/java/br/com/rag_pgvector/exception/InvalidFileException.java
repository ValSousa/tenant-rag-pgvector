package br.com.rag_pgvector.exception;

/**
 * Arquivo enviado inválido (vazio, sem nome, nome longo demais ou que não é PDF); vira 400 no
 * {@link GlobalExceptionHandler}. A mensagem é mostrada ao usuário, então é escrita em português.
 */
public class InvalidFileException extends RuntimeException {

	public InvalidFileException(String message) {
		super(message);
	}

}
