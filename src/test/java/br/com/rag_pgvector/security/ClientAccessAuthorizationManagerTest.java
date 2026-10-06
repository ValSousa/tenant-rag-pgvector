package br.com.rag_pgvector.security;

import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Decisões do {@link ClientAccessAuthorizationManager} (RF-009 RN-01), inclusive o caminho em que o principal não é
 * um {@link ClientPrincipal} (revisão do RF-003, pendência do RF-009).
 */
class ClientAccessAuthorizationManagerTest {

	private final ClientAccessAuthorizationManager manager = new ClientAccessAuthorizationManager();

	@Test
	@DisplayName("RF-009 RN-01 — Cliente acessa o próprio clientId")
	void deveConcederAoProprioCliente() {
		assertThat(decidir(cliente(CLIENTE_A), String.valueOf(CLIENTE_A))).isTrue();
	}

	@ParameterizedTest(name = "[{index}] clientId = {0}")
	@ValueSource(strings = { "2", "999", "abc", "", "1.0", "99999999999999999999" })
	@DisplayName("RF-009 RN-01 — Cliente não acessa outro clientId nem um clientId inválido")
	void naoDeveConcederAOutroClienteOuIdInvalido(String clientId) {
		assertThat(decidir(cliente(CLIENTE_A), clientId)).isFalse();
	}

	@Test
	@DisplayName("RF-009 DP-02 — Administrador não acessa /clients/{clientId}/** (principal não é ClientPrincipal)")
	void naoDeveConcederAoAdministrador() {
		Authentication admin = UsernamePasswordAuthenticationToken.authenticated("admin", null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

		assertThat(decidir(admin, String.valueOf(CLIENTE_A))).isFalse();
	}

	@Test
	@DisplayName("RF-009 FA-01 — Sem autenticação ou sem clientId no caminho é negado")
	void naoDeveConcederSemAutenticacaoOuSemClientId() {
		assertThat(decidir(null, String.valueOf(CLIENTE_A))).isFalse();
		assertThat(manager.authorize(() -> cliente(CLIENTE_A),
				new RequestAuthorizationContext(new MockHttpServletRequest(), Map.of())).isGranted()).isFalse();
	}

	private boolean decidir(Authentication authentication, String clientId) {
		var context = new RequestAuthorizationContext(new MockHttpServletRequest(), Map.of("clientId", clientId));
		return manager.authorize(() -> authentication, context).isGranted();
	}

	private static Authentication cliente(long clientId) {
		return UsernamePasswordAuthenticationToken.authenticated(new ClientPrincipal(clientId), null,
				List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
	}

}
