package br.com.rag_pgvector.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Repassa os erros 401 e 403 do Spring Security ao {@code GlobalExceptionHandler}, para que saiam no mesmo
 * formato {@code ProblemDetail} dos demais erros (ADR-010).
 */
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final HandlerExceptionResolver handlerExceptionResolver;

	public ProblemDetailSecurityHandler(HandlerExceptionResolver handlerExceptionResolver) {
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) {
		handlerExceptionResolver.resolveException(request, response, null, authException);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) {
		handlerExceptionResolver.resolveException(request, response, null, accessDeniedException);
	}

}
