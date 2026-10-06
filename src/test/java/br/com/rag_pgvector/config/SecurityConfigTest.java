package br.com.rag_pgvector.config;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CHAVE_ADMIN;
import static br.com.rag_pgvector.support.Fixtures.CHAVE_B;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import br.com.rag_pgvector.dto.ClientCreatedResponseDTO;
import br.com.rag_pgvector.dto.ClientResponseDTO;
import br.com.rag_pgvector.dto.DocumentResponseDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.DdtValores;
import br.com.rag_pgvector.support.TestPdfFactory;
import br.com.rag_pgvector.support.WebSliceTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Regras de acesso por chave e rota (RF-009, ADR-008, arquitetura 07, seção 2). O springdoc é carregado na fatia web
 * para que a matriz e o CT-085 cheguem à documentação real, e não a um 404.
 */
@ImportAutoConfiguration({ SpringDocConfiguration.class, SpringDocConfigProperties.class,
		SpringDocWebMvcConfiguration.class, SwaggerConfig.class, SwaggerUiConfigProperties.class,
		SwaggerUiOAuthProperties.class })
@Import(OpenApiConfig.class)
class SecurityConfigTest extends WebSliceTest {

	private static final Map<String, String> CHAVES = Map.of(
			"CHAVE_A", CHAVE_A,
			"CHAVE_B", CHAVE_B,
			"CHAVE_ADMIN", CHAVE_ADMIN,
			"CHAVE_INVALIDA", "chave-que-nao-existe");

	private static final String PERGUNTA = "{\"question\":\"Qual é o valor da franquia?\"}";

	private static final List<String> ROTAS_DE_DADOS = List.of("search", "documents", "ask");

	@ParameterizedTest(name = "[{index}] CT-080 — {0}: {2} {3} com {1} → {4}")
	@CsvFileSource(resources = "/ddt/security-matrix.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-080 — Matriz de acesso por chave e rota (DDT)")
	void deveAplicarAMatrizDeAcesso(String cenario, String chave, String metodo, String rota, String esperado)
			throws Exception {
		// Respostas dos services para as linhas PERMITIDO chegarem ao controller e saírem com 2xx
		when(clientService.create(anyString())).thenReturn(new ClientCreatedResponseDTO(3L, "Cliente C", "chave-c"));
		when(documentService.ingest(anyLong(), anyString(), any(), any())).thenReturn(new DocumentResponseDTO(10L,
				CLIENTE_A, "contrato.pdf", DocumentTypeEnum.CONTRACT, Instant.parse("2026-10-02T12:00:00Z"), 3));

		int status = mvc.perform(requisicao(HttpMethod.valueOf(metodo), rota, chaveDaMassa(chave)))
				.andReturn().getResponse().getStatus();

		if ("PERMITIDO".equals(esperado)) {
			// Permitido pela segurança e atendido pelo controller (o swagger-ui.html redireciona para o index)
			assertThat(status).as(cenario).isBetween(200, 399);
		}
		else {
			assertThat(status).as(cenario).isEqualTo(Integer.parseInt(esperado));
		}
	}

	@Test
	@DisplayName("CT-081 — 403 não revela se o outro cliente existe")
	void naoDeveRevelarSeOOutroClienteExiste() throws Exception {
		// O Cliente B existe (tem chave no WebSliceTest); o 999 não
		String clienteExistente = corpoDo403(post("/clients/{id}/search", CLIENTE_B));
		String clienteInexistente = corpoDo403(post("/clients/{id}/search", 999));

		assertThat(semInstance(clienteExistente)).isEqualTo(semInstance(clienteInexistente));
		assertThat(clienteExistente).doesNotContain("Cliente B").doesNotContain("999");
		verifyNoInteractions(clientService, searchService);
	}

	@Test
	@DisplayName("CT-082 — Acesso negado não chega ao service")
	void naoDeveChegarAoServiceQuandoOAcessoENegado() throws Exception {
		for (String rota : ROTAS_DE_DADOS) {
			mvc.perform(requisicao(HttpMethod.POST, "/clients/" + CLIENTE_B + "/" + rota, CHAVE_A))
					.andExpect(status().isForbidden());
		}

		verifyNoInteractions(searchService, documentService, answerService, clientService);
	}

	@Test
	@DisplayName("CT-084 — clientId não numérico")
	void naoDeveAceitarClientIdNaoNumerico() throws Exception {
		mvc.perform(requisicao(HttpMethod.POST, "/clients/abc/search", CHAVE_A))
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Acesso negado"));

		verifyNoInteractions(searchService);
	}

	@Test
	@DisplayName("CT-085 — Documentação pública, operações protegidas")
	void deveDeixarADocumentacaoPublicaEDeclararAChave() throws Exception {
		mvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());
		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.apiKey.type").value("apiKey"))
				.andExpect(jsonPath("$.components.securitySchemes.apiKey.in").value("header"))
				.andExpect(jsonPath("$.components.securitySchemes.apiKey.name").value(ApiKeyAuthenticationFilter.HEADER))
				.andExpect(jsonPath("$.security[0].apiKey").isArray());

		mvc.perform(requisicao(HttpMethod.POST, "/clients/" + CLIENTE_A + "/search", null))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("CT-115 — Acesso cruzado por credencial em todas as rotas de dados")
	void naoDevePermitirAcessoCruzadoNasRotasDeDados() throws Exception {
		for (String rota : ROTAS_DE_DADOS) {
			mvc.perform(requisicao(HttpMethod.POST, "/clients/" + CLIENTE_B + "/" + rota, CHAVE_A))
					.andExpect(status().isForbidden())
					.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Acesso negado"));
			mvc.perform(requisicao(HttpMethod.POST, "/clients/" + CLIENTE_A + "/" + rota, CHAVE_B))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.title").value("Acesso negado"));
		}

		verifyNoInteractions(searchService, documentService, answerService);
	}

	@Test
	@DisplayName("Revisão RF-003 #3 — PUT, DELETE e PATCH em /clients/{id} são negados até para o próprio cliente")
	void naoDevePermitirOutrosMetodosNaRotaDoCliente() throws Exception {
		for (HttpMethod metodo : List.of(HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)) {
			mvc.perform(requisicao(metodo, "/clients/" + CLIENTE_A, CHAVE_A))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.title").value("Acesso negado"));
		}

		verifyNoInteractions(clientService);
	}

	@Test
	@DisplayName("CT-025 — Cliente consulta a si mesmo, mas não a outro")
	void deveDeixarClienteConsultarSoASiMesmo() throws Exception {
		when(clientService.findById(CLIENTE_A))
				.thenReturn(new ClientResponseDTO(CLIENTE_A, "Cliente A", Instant.parse("2026-10-01T13:45:00Z")));

		mvc.perform(get("/clients/{id}", CLIENTE_A).header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(CLIENTE_A));
		mvc.perform(get("/clients/{id}", CLIENTE_B).header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A))
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Acesso negado"));

		verify(clientService, never()).findById(CLIENTE_B);
	}

	@Test
	@DisplayName("CT-027 — Cliente não pode cadastrar clientes")
	void naoDeveDeixarClienteCadastrarClientes() throws Exception {
		mvc.perform(post("/clients")
						.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Cliente C\"}"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Acesso negado"));

		verifyNoInteractions(clientService);
	}

	@Test
	@DisplayName("CT-080 (linhas do RF-003) — Sem chave ou com chave desconhecida recebe 401")
	void deveResponder401SemChaveOuComChaveDesconhecida() throws Exception {
		mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cliente C\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Não autenticado"));
		mvc.perform(get("/clients/{id}", CLIENTE_A).header(ApiKeyAuthenticationFilter.HEADER, "chave-desconhecida"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Não autenticado"));

		verifyNoInteractions(clientService);
	}

	@Test
	@DisplayName("CT-092 / Revisão RF-003 #1 — Falha do banco ao consultar a chave devolve 500, não 401")
	void deveResponder500QuandoConsultaDaChaveFalha() throws Exception {
		when(clientRepository.findByApiKeyHash(anyString()))
				.thenThrow(new DataAccessResourceFailureException("SELECT * FROM client: conexão recusada"));

		mvc.perform(get("/clients/{id}", CLIENTE_A).header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Erro interno"))
				.andExpect(content().string(not(containsString("SELECT"))))
				.andExpect(content().string(not(containsString("Exception"))));

		verifyNoInteractions(clientService);
	}

	private static String chaveDaMassa(String valor) {
		String nome = DdtValores.resolver(valor);
		return nome == null ? null : CHAVES.get(nome);
	}

	/** Monta a requisição com um corpo válido para a rota, para que só a segurança decida o 401 ou o 403. */
	private static AbstractMockHttpServletRequestBuilder<?> requisicao(HttpMethod metodo, String rota, String chave) {
		AbstractMockHttpServletRequestBuilder<?> builder;
		if (rota.endsWith("/documents")) {
			builder = multipart(metodo, rota)
					.file(new MockMultipartFile("file", "contrato.pdf", "application/pdf",
							TestPdfFactory.comTexto("A franquia é de R$ 3.500,00.")))
					.param("documentType", "CONTRACT");
		}
		else if (rota.endsWith("/search") || rota.endsWith("/ask")) {
			builder = request(metodo, rota).contentType(MediaType.APPLICATION_JSON).content(PERGUNTA);
		}
		else if (rota.startsWith("/clients") && !HttpMethod.GET.equals(metodo)) {
			builder = request(metodo, rota).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cliente C\"}");
		}
		else {
			builder = request(metodo, rota);
		}
		return chave == null ? builder : builder.header(ApiKeyAuthenticationFilter.HEADER, chave);
	}

	private String corpoDo403(MockHttpServletRequestBuilder builder) throws Exception {
		return mvc.perform(builder.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
						.contentType(MediaType.APPLICATION_JSON)
						.content(PERGUNTA))
				.andExpect(status().isForbidden())
				.andReturn().getResponse().getContentAsString();
	}

	private static Map<String, Object> semInstance(String json) {
		Map<String, Object> corpo = JsonPath.parse(json).read("$");
		corpo.remove("instance");
		return corpo;
	}

}
