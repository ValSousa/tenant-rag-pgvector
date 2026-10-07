package br.com.rag_pgvector;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_ADMIN;
import static br.com.rag_pgvector.support.LogCapturado.campo;
import static br.com.rag_pgvector.support.LogCapturado.linhasDeRequisicao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Locale;

import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.security.ApiKeyHasher;
import br.com.rag_pgvector.support.AbstractIntegrationTest;
import br.com.rag_pgvector.support.PostgresTestcontainersConfig;
import br.com.rag_pgvector.support.TestPdfFactory;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Nenhum segredo nem dado do cliente no log (ADR-015, decisão 7), com contexto completo, banco real e os modelos
 * falsos: o log é o texto capturado pelo {@code OutputCaptureExtension} durante as requisições.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingIT extends AbstractIntegrationTest {

	private static final String CHAVE_OPENAI = "test-key-nao-usada";
	private static final String CHAVE_INVALIDA = "chave-invalida-log-it";
	private static final String USER_AGENT = "AgenteRastreioLog/1.0";

	@Test
	@DisplayName("CT-146 — Nenhuma chave, hash ou senha aparece no log")
	void naoDeveRegistrarChavesHashesNemSenhas(CapturedOutput output) throws Exception {
		Cadastro cliente = cadastrar("Cliente A");
		String hashGravado = jdbc.queryForObject("SELECT api_key_hash FROM client WHERE id = ?", String.class,
				cliente.id());

		enviar(cliente, "contrato.pdf", "A franquia é de R$ 3.500,00.").andExpect(status().isCreated());
		perguntar(cliente, "search", "Qual é o valor da franquia?").andExpect(status().isOk());
		perguntar(cliente, "ask", "Qual é o valor da franquia?").andExpect(status().isOk());
		mvc.perform(get("/clients/{id}", cliente.id()).header(ApiKeyAuthenticationFilter.HEADER, cliente.apiKey()))
				.andExpect(status().isOk());
		mvc.perform(get("/clients/{id}", cliente.id()).header(ApiKeyAuthenticationFilter.HEADER, CHAVE_INVALIDA))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/clients/{id}", cliente.id() + 1).header(ApiKeyAuthenticationFilter.HEADER, cliente.apiKey()))
				.andExpect(status().isForbidden());

		List<String> linhas = linhasDeRequisicao(output);
		assertThat(linhas).extracting(linha -> campo(linha, "status"))
				.containsExactly("201", "201", "200", "200", "200", "401", "403");
		assertThat(hashGravado).isEqualTo(ApiKeyHasher.hash(cliente.apiKey()));
		assertThat(output.getAll()).doesNotContain(cliente.apiKey(), hashGravado, CHAVE_ADMIN, CHAVE_OPENAI,
				PostgresTestcontainersConfig.SENHA_DO_BANCO, CHAVE_INVALIDA);

		assertThat(fakeEmbeddings.chamadas()).isPositive();
		assertThat(fakeChat.requisicoes()).hasSize(1);
	}

	@Test
	@DisplayName("CT-147 — Linhas de /ask, /search e /documents sem pergunta, texto, nome de arquivo ou nome do cliente")
	void naoDeveRegistrarDadosDoClienteNemDaRequisicao(CapturedOutput output) throws Exception {
		Cadastro cliente = cadastrar("Segurado Rastreio Log");

		enviar(cliente, "apolice-rastreio-log.pdf", "Franquia exclusiva R$ 7.777,77").andExpect(status().isCreated());
		perguntar(cliente, "search", "Qual a franquia rastreio log?").andExpect(status().isOk());
		perguntar(cliente, "ask", "Qual a franquia rastreio log?").andExpect(status().isOk());

		List<String> linhas = linhasDeRequisicao(output);
		assertThat(linhas).extracting(linha -> campo(linha, "endpoint") + " " + campo(linha, "status"))
				.containsExactly("/clients 201", "/clients/" + cliente.id() + "/documents 201",
						"/clients/" + cliente.id() + "/search 200", "/clients/" + cliente.id() + "/ask 200");
		String log = output.getAll();
		assertThat(log).doesNotContain("Segurado Rastreio Log", "apolice-rastreio-log.pdf", "7.777,77",
				"AgenteRastreioLog");
		assertThat(log.toLowerCase(Locale.ROOT)).doesNotContain("franquia rastreio log", "franquia exclusiva");
		assertThat(fakeChat.requisicoes()).hasSize(1);
	}

	private Cadastro cadastrar(String nome) throws Exception {
		String resposta = mvc.perform(post("/clients")
						.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_ADMIN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + nome + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return new Cadastro(JsonPath.<Number>read(resposta, "$.id").longValue(), JsonPath.read(resposta, "$.apiKey"));
	}

	private ResultActions enviar(Cadastro cliente, String nomeDoArquivo,
			String texto) throws Exception {
		return mvc.perform(multipart("/clients/{clientId}/documents", cliente.id())
				.file(new MockMultipartFile("file", nomeDoArquivo, MediaType.APPLICATION_PDF_VALUE,
						TestPdfFactory.comTexto(texto)))
				.param("documentType", "CONTRACT")
				.header(ApiKeyAuthenticationFilter.HEADER, cliente.apiKey())
				.header(HttpHeaders.USER_AGENT, USER_AGENT));
	}

	private ResultActions perguntar(Cadastro cliente, String rota,
			String pergunta) throws Exception {
		return mvc.perform(post("/clients/{clientId}/" + rota, cliente.id())
				.header(ApiKeyAuthenticationFilter.HEADER, cliente.apiKey())
				.header(HttpHeaders.USER_AGENT, USER_AGENT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\":\"" + pergunta + "\"}"));
	}

	private record Cadastro(long id, String apiKey) {
	}

}
