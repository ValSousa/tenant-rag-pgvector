package br.com.rag_pgvector.config;

import br.com.rag_pgvector.repository.ClientRepository;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.security.ClientAccessAuthorizationManager;
import br.com.rag_pgvector.security.ProblemDetailSecurityHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.util.Assert;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Autenticação por chave de API e regras de rota (ADR-008, arquitetura 07, seção 2).
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Value("${app.security.admin-api-key}") String adminApiKey,
			ClientRepository clientRepository,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
		Assert.hasText(adminApiKey, "app.security.admin-api-key (ADMIN_API_KEY) não pode ser vazia");
		var problemDetailHandler = new ProblemDetailSecurityHandler(handlerExceptionResolver);
		var clientAccess = new ClientAccessAuthorizationManager();

		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(problemDetailHandler)
						.accessDeniedHandler(problemDetailHandler))
				.addFilterBefore(new ApiKeyAuthenticationFilter(adminApiKey, clientRepository, problemDetailHandler,
						handlerExceptionResolver), AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/clients").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/clients/{clientId}").access(AuthorizationManagers.anyOf(
								AuthorityAuthorizationManager.<RequestAuthorizationContext>hasRole("ADMIN"),
								clientAccess))
						.requestMatchers("/clients/{clientId}").denyAll()
						.requestMatchers("/clients/{clientId}/**").access(clientAccess)
						.anyRequest().denyAll());
		return http.build();
	}

}
