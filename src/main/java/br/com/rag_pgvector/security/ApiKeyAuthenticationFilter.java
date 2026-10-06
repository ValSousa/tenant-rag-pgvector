package br.com.rag_pgvector.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

import br.com.rag_pgvector.repository.ClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Autentica pelo cabeçalho {@code X-API-Key} (ADR-008): chave de administrador → {@code ROLE_ADMIN}; chave de cliente
 * → {@link ClientPrincipal} com {@code ROLE_CLIENT}. Sem cabeçalho a requisição segue anônima; chave desconhecida
 * recebe 401. A chave nunca é registrada em log. Falha ao consultar a chave (ex.: banco fora do ar) vai para o
 * {@code GlobalExceptionHandler} e vira 500: se saísse do filtro, o despacho para {@code /error} chegaria sem
 * autenticação e viraria um 401 enganoso.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-API-Key";

	private final byte[] adminApiKey;
	private final ClientRepository clientRepository;
	private final AuthenticationEntryPoint authenticationEntryPoint;
	private final HandlerExceptionResolver handlerExceptionResolver;
	private final SecurityContextHolderStrategy securityContextHolderStrategy =
			SecurityContextHolder.getContextHolderStrategy();

	public ApiKeyAuthenticationFilter(String adminApiKey, ClientRepository clientRepository,
			AuthenticationEntryPoint authenticationEntryPoint, HandlerExceptionResolver handlerExceptionResolver) {
		this.adminApiKey = adminApiKey.getBytes(StandardCharsets.UTF_8);
		this.clientRepository = clientRepository;
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String apiKey = request.getHeader(HEADER);
		if (apiKey == null || apiKey.isBlank()) {
			chain.doFilter(request, response);
			return;
		}

		Optional<Authentication> authentication;
		try {
			authentication = authenticate(apiKey);
		}
		catch (RuntimeException ex) {
			securityContextHolderStrategy.clearContext();
			handlerExceptionResolver.resolveException(request, response, null, ex);
			return;
		}
		if (authentication.isEmpty()) {
			securityContextHolderStrategy.clearContext();
			authenticationEntryPoint.commence(request, response,
					new BadCredentialsException("Chave de API desconhecida"));
			return;
		}

		SecurityContext context = securityContextHolderStrategy.createEmptyContext();
		context.setAuthentication(authentication.get());
		securityContextHolderStrategy.setContext(context);
		chain.doFilter(request, response);
	}

	private Optional<Authentication> authenticate(String apiKey) {
		// Comparação em tempo constante
		if (MessageDigest.isEqual(adminApiKey, apiKey.getBytes(StandardCharsets.UTF_8))) {
			return Optional.of(UsernamePasswordAuthenticationToken.authenticated("admin", null,
					List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		}
		return clientRepository.findByApiKeyHash(ApiKeyHasher.hash(apiKey))
				.map(client -> UsernamePasswordAuthenticationToken.authenticated(
						new ClientPrincipal(client.getId()), null,
						List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))));
	}

}
