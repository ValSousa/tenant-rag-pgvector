package br.com.rag_pgvector;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CHAVE_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.security.ApiKeyHasher;
import br.com.rag_pgvector.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Fluxo da API de ponta a ponta com banco real (docs/QA/03, seção 8): cadastro → autenticação → busca → resposta
 * ({@code /ask} com o {@code RagAssistant} real sobre o {@code FakeChatModel}).
 */
class ApiFlowIT extends AbstractIntegrationTest {

	@Test
	@DisplayName("CT-020 — Administrador cadastra cliente e a chave devolvida autentica o cliente")
	void deveCadastrarClienteEAutenticarComAChaveDevolvida() throws Exception {
		Cadastro cadastro = cadastrar("Cliente A");

		mvc.perform(get("/clients/{id}", cadastro.id()).header(ApiKeyAuthenticationFilter.HEADER, cadastro.apiKey()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Cliente A"));
	}

	@Test
	@DisplayName("CT-023 — Consulta de cliente existente")
	void deveConsultarClienteExistente() throws Exception {
		Cadastro cadastro = cadastrar("Cliente B");

		mvc.perform(get("/clients/{id}", cadastro.id()).header(ApiKeyAuthenticationFilter.HEADER, CHAVE_ADMIN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(cadastro.id()))
				.andExpect(jsonPath("$.name").value("Cliente B"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	@DisplayName("CT-083 — Chave guardada só como hash")
	void deveGuardarSoOHashDaChave() throws Exception {
		Cadastro cadastro = cadastrar("Cliente A");

		String gravado = jdbc.queryForObject("SELECT api_key_hash FROM client WHERE id = ?", String.class,
				cadastro.id());

		assertThat(gravado)
				.isNotEqualTo(cadastro.apiKey())
				.isEqualTo(ApiKeyHasher.hash(cadastro.apiKey()));
	}

	@Test
	@DisplayName("CT-063 — Cliente sem documentos recebe lista vazia")
	void clienteSemDocumentosDeveReceberListaVazia() throws Exception {
		// Outro cliente com documento e chunk: a busca do cliente novo continua vazia (RF-007 FA-03)
		ClientEntity outro = data.cliente("Cliente B");
		data.chunks(data.documento(outro, "contrato.pdf", DocumentTypeEnum.CONTRACT),
				List.of("Franquia de R$ 5.000,00."), List.of(fakeEmbeddings.fixo("franquia")));
		fakeEmbeddings.registrar("Qual é o valor da franquia?", fakeEmbeddings.fixo("franquia"));
		Cadastro cadastro = cadastrar("Cliente A");

		mvc.perform(post("/clients/{id}/search", cadastro.id())
						.header(ApiKeyAuthenticationFilter.HEADER, cadastro.apiKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"Qual é o valor da franquia?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value(cadastro.id()))
				.andExpect(jsonPath("$.results").value(empty()));
	}

	@Test
	@DisplayName("CT-070 (integração) — /ask de cliente sem documentos responde sem chamar o modelo")
	void askSemDocumentosNaoDeveChamarOModelo() throws Exception {
		ClientEntity outro = data.cliente("Cliente B");
		data.chunks(data.documento(outro, "contrato.pdf", DocumentTypeEnum.CONTRACT),
				List.of("Franquia de R$ 5.000,00."), List.of(fakeEmbeddings.fixo("franquia")));
		Cadastro cadastro = cadastrar("Cliente A");

		mvc.perform(post("/clients/{id}/ask", cadastro.id())
						.header(ApiKeyAuthenticationFilter.HEADER, cadastro.apiKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"Qual é o valor da franquia?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("Não há documentos deste cliente para responder a esta pergunta."))
				.andExpect(jsonPath("$.sources").value(empty()));

		assertThat(fakeChat.requisicoes()).isEmpty();
	}

	@Test
	@DisplayName("CT-072 (integração) — Falha do modelo de chat no /ask responde 503")
	void askDeveResponder503QuandoOModeloDeChatFalha() throws Exception {
		ClientEntity cliente = data.cliente("Cliente A", CHAVE_A);
		data.chunks(data.documento(cliente, "contrato.pdf", DocumentTypeEnum.CONTRACT),
				List.of("Franquia de R$ 4.000,00."), List.of(fakeEmbeddings.fixo("franquia")));
		fakeChat.falharNaProxima(new RuntimeException("HTTP 503 do provedor"));

		mvc.perform(post("/clients/{id}/ask", cliente.getId())
						.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"Qual é o valor da franquia?\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Serviço de IA indisponível"))
				.andExpect(content().string(not(containsString("HTTP 503 do provedor"))));

		assertThat(fakeChat.requisicoes()).hasSize(1);
	}

	private Cadastro cadastrar(String nome) throws Exception {
		String resposta = mvc.perform(post("/clients")
						.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_ADMIN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + nome + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/clients/")))
				.andReturn().getResponse().getContentAsString();
		long id = JsonPath.<Number>read(resposta, "$.id").longValue();
		String apiKey = JsonPath.read(resposta, "$.apiKey");
		assertThat(apiKey).isNotBlank();
		return new Cadastro(id, apiKey);
	}

	private record Cadastro(long id, String apiKey) {
	}

}
