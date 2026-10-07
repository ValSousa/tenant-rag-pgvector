package br.com.rag_pgvector.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Uma linha de log por requisição da API (ADR-015), com trace ID no MDC para que todas as linhas da requisição o
 * tragam (via {@code logging.pattern.correlation}) e no cabeçalho de resposta {@code X-Trace-Id}.
 * Roda antes da cadeia do Spring Security (ordem -100), então registra também os 401/403.
 * Só registra campos fixos: nunca lê cabeçalhos, corpo, query string, IP nem User-Agent. O trace ID é sempre gerado
 * aqui; valor vindo do cliente não é aceito.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

	public static final String TRACE_ID_HEADER = "X-Trace-Id";
	public static final String TRACE_ID_MDC_KEY = "traceId";

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
	private static final Pattern CLIENT_PATH = Pattern.compile("^/clients/(\\d+)(/.*)?$");

	private final String service;
	private final String environment;

	public RequestLoggingFilter(@Value("${spring.application.name}") String service,
			@Value("${app.logging.environment}") String environment) {
		this.service = service;
		this.environment = environment;
	}

	/** Swagger UI e documentação OpenAPI ficam fora do log. */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long start = System.nanoTime();
		String traceId = UUID.randomUUID().toString().replace("-", "");
		MDC.put(TRACE_ID_MDC_KEY, traceId);
		response.setHeader(TRACE_ID_HEADER, traceId);
		int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		try {
			chain.doFilter(request, response);
			status = response.getStatus();
		}
		finally {
			long durationMs = (System.nanoTime() - start) / 1_000_000;
			String endpoint = request.getRequestURI();
			log.atLevel(levelFor(status)).log(
					"service={} environment={} method={} endpoint={} status={} clientId={} traceId={} duration={}ms",
					service, environment, request.getMethod(), endpoint, status, clientIdFrom(endpoint), traceId,
					durationMs);
			MDC.remove(TRACE_ID_MDC_KEY);
		}
	}

	static Level levelFor(int status) {
		if (status >= 200 && status < 300) {
			return Level.INFO; // sucesso
		}
		if (status >= 500) {
			return Level.ERROR; // erro de servidor
		}
		if (status >= 400) {
			return Level.ERROR; // erro do cliente / autorização
		}
		return Level.INFO;
	}

	static String clientIdFrom(String path) {
		Matcher matcher = CLIENT_PATH.matcher(path);
		return matcher.matches() ? matcher.group(1) : "-";
	}

}
