package br.com.rag_pgvector.security;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Concede acesso a {@code /clients/{clientId}/**} só ao próprio cliente. Um {@code clientId} que não é
 * número, ou de outro cliente (exista ou não), é negado da mesma forma.
 */
public class ClientAccessAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
			RequestAuthorizationContext context) {
		Authentication auth = authentication.get();
		if (auth == null || !(auth.getPrincipal() instanceof ClientPrincipal principal)) {
			return new AuthorizationDecision(false);
		}
		return new AuthorizationDecision(isSameClient(principal, context.getVariables().get("clientId")));
	}

	private static boolean isSameClient(ClientPrincipal principal, String clientId) {
		try {
			return clientId != null && Long.parseLong(clientId) == principal.clientId();
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

}
