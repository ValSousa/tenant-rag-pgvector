package br.com.rag_pgvector.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converte exceções em {@link ProblemDetail} (RFC 9457) com títulos em português (ADR-010). Também atende os erros
 * 401 e 403 do Spring Security, repassados pelo {@code ProblemDetailSecurityHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Tipo genérico da RFC 9457; o Spring 7 deixa o {@code type} nulo e o omite do JSON, e a ADR-010 quer o campo. */
	private static final URI TIPO_PADRAO = URI.create("about:blank");

	private static final Map<Integer, String> TITULOS = Map.ofEntries(
			Map.entry(400, "Requisição inválida"),
			Map.entry(401, "Não autenticado"),
			Map.entry(403, "Acesso negado"),
			Map.entry(404, "Recurso não encontrado"),
			Map.entry(405, "Método não permitido"),
			Map.entry(406, "Formato de resposta não suportado"),
			Map.entry(413, "Arquivo muito grande"),
			Map.entry(415, "Tipo de conteúdo não suportado"),
			Map.entry(422, "Documento não processável"),
			Map.entry(500, "Erro interno"),
			Map.entry(503, "Serviço de IA indisponível"));

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(InvalidFileException.class)
	ProblemDetail handleInvalidFile(InvalidFileException ex) {
		return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(InvalidDocumentException.class)
	ProblemDetail handleInvalidDocument(InvalidDocumentException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

	@ExceptionHandler(AiProviderException.class)
	ProblemDetail handleAiProvider(AiProviderException ex) {
		log.error("Falha no provedor de IA: {}", ex.getMessage(), ex);
		return problem(HttpStatus.SERVICE_UNAVAILABLE,
				"O serviço de IA está indisponível no momento. Tente novamente mais tarde.");
	}

	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleAuthentication(AuthenticationException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Informe uma chave de API válida no cabeçalho X-API-Key.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, "Você não tem permissão para acessar este recurso.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Erro inesperado", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
		List<FieldErrorDTO> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldErrorDTO(error.getField(), error.getDefaultMessage()))
				.toList();
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body == null && ex instanceof ErrorResponse errorResponse) {
			body = errorResponse.getBody();
		}
		if (body instanceof ProblemDetail problemDetail) {
			if (problemDetail.getType() == null) {
				problemDetail.setType(TIPO_PADRAO);
			}
			if (TITULOS.containsKey(statusCode.value())) {
				problemDetail.setTitle(TITULOS.get(statusCode.value()));
			}
			String detail = detalheEmPortugues(ex);
			if (detail != null) {
				problemDetail.setDetail(detail);
			}
		}
		return super.handleExceptionInternal(ex, body, headers, statusCode, request);
	}

	private static String detalheEmPortugues(Exception ex) {
		return switch (ex) {
			case MaxUploadSizeExceededException e -> "O arquivo excede o tamanho máximo permitido de 5 MB.";
			case MissingServletRequestPartException e -> "A parte '%s' é obrigatória.".formatted(e.getRequestPartName());
			case MissingServletRequestParameterException e ->
					"O parâmetro '%s' é obrigatório.".formatted(e.getParameterName());
			case TypeMismatchException e -> "O valor informado em '%s' é inválido.".formatted(e.getPropertyName());
			case HttpMessageNotReadableException e -> "O corpo da requisição está ausente ou malformado.";
			case HttpRequestMethodNotSupportedException e ->
					"O método %s não é suportado neste endereço.".formatted(e.getMethod());
			case HttpMediaTypeNotSupportedException e -> "O tipo de conteúdo '%s' não é suportado neste endereço."
					.formatted(e.getContentType());
			case HttpMediaTypeNotAcceptableException e -> "O formato de resposta pedido não é suportado.";
			case NoResourceFoundException e -> "O endereço solicitado não existe.";
			default -> null;
		};
	}

	private static ProblemDetail problem(HttpStatus status, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(TIPO_PADRAO);
		problem.setTitle(TITULOS.get(status.value()));
		return problem;
	}

	public record FieldErrorDTO(String field, String message) {
	}

}
