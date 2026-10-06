package br.com.rag_pgvector.exception;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.WebSliceTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * CT-090 a CT-093 (RF-010): mapeamento de exceções para HTTP, formato {@code ProblemDetail}, erro interno sem
 * detalhes técnicos e lista de campos nos erros de validação. Um controller só de teste lança a exceção pedida.
 */
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest extends WebSliceTest {

	private static final String ROTA_DE_TESTE = "/clients/{clientId}/teste-erro";

	@AfterEach
	void limparExcecao() {
		ThrowingController.excecao = null;
	}

	static Stream<Arguments> excecoes() {
		return Stream.of(
				arguments(new ResourceNotFoundException("Cliente 999 não encontrado"), 404, "Recurso não encontrado"),
				arguments(new InvalidFileException("O arquivo deve ser um PDF."), 400, "Requisição inválida"),
				arguments(new InvalidDocumentException("O documento não contém texto extraível."), 422,
						"Documento não processável"),
				arguments(new AiProviderException("OpenAI indisponível"), 503, "Serviço de IA indisponível"),
				arguments(new MaxUploadSizeExceededException(10_485_760), 413, "Arquivo muito grande"),
				arguments(new IllegalStateException("erro inesperado"), 500, "Erro interno"));
	}

	@ParameterizedTest(name = "[{index}] {0} -> {1}")
	@MethodSource("excecoes")
	@DisplayName("CT-090 — Mapeamento de exceções para HTTP (DDT)")
	void deveMapearCadaExcecaoParaOStatusEOTitulo(RuntimeException excecao, int esperado, String titulo)
			throws Exception {
		ThrowingController.excecao = excecao;

		String corpo = mvc.perform(chamarRotaDeTeste())
				.andExpect(status().is(esperado))
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(esperado))
				.andExpect(jsonPath("$.title").value(titulo))
				.andReturn().getResponse().getContentAsString();

		assertThat(corpo).doesNotContain(excecao.getClass().getSimpleName(), "at br.com.");
	}

	static Stream<Arguments> errosTratados() {
		return Stream.of(
				arguments("404 do service", (RequestBuilder) chamarRotaDeTeste(), 404),
				arguments("400 de validação", busca("{\"question\":\"\"}"), 400),
				arguments("400 de JSON malformado", busca("{\"question\":"), 400),
				arguments("401 sem chave", get(ROTA_DE_TESTE, CLIENTE_A), 401),
				arguments("403 de outro cliente",
						get(ROTA_DE_TESTE, CLIENTE_B).header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A), 403),
				arguments("405 de método não suportado",
						put("/clients/{clientId}/search", CLIENTE_A).header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A),
						405),
				arguments("415 de tipo de conteúdo",
						post("/clients/{clientId}/search", CLIENTE_A)
								.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
								.contentType(MediaType.TEXT_PLAIN)
								.content("pergunta"),
						415),
				arguments("500 inesperado", chamarRotaDeTeste(), 500));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("errosTratados")
	@DisplayName("CT-091 — Formato ProblemDetail")
	void deveResponderNoFormatoProblemDetail(String cenario, RequestBuilder requisicao, int esperado)
			throws Exception {
		ThrowingController.excecao = esperado == 404 ? new ResourceNotFoundException("Cliente 999 não encontrado")
				: new IllegalStateException("falha");

		mvc.perform(requisicao)
				.andExpect(status().is(esperado))
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").isNotEmpty())
				.andExpect(jsonPath("$.title").isNotEmpty())
				.andExpect(jsonPath("$.status").value(esperado))
				.andExpect(jsonPath("$.detail").isNotEmpty())
				.andExpect(jsonPath("$.instance").isNotEmpty());
	}

	@Test
	@DisplayName("CT-091 — Erros gerados pelo Spring MVC saem com título e detalhe em português")
	void deveTraduzirOsErrosDoSpringMvc() throws Exception {
		mvc.perform(put("/clients/{clientId}/search", CLIENTE_A).header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.title").value("Método não permitido"))
				.andExpect(jsonPath("$.detail").value("O método PUT não é suportado neste endereço."));

		mvc.perform(post("/clients/{clientId}/search", CLIENTE_A)
						.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
						.contentType(MediaType.TEXT_PLAIN)
						.content("pergunta"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.title").value("Tipo de conteúdo não suportado"))
				.andExpect(jsonPath("$.detail").value(
						"O tipo de conteúdo 'text/plain;charset=UTF-8' não é suportado neste endereço."));

		mvc.perform(busca("{\"question\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Requisição inválida"))
				.andExpect(jsonPath("$.detail").value("O corpo da requisição está ausente ou malformado."));
	}

	@Test
	@DisplayName("CT-091 — Arquivo inválido sai com o ProblemDetail completo de 400")
	void deveResponderArquivoInvalidoComProblemDetailCompleto() throws Exception {
		ThrowingController.excecao = new InvalidFileException("O arquivo enviado está vazio.");

		mvc.perform(chamarRotaDeTeste())
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Requisição inválida"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("O arquivo enviado está vazio."))
				.andExpect(jsonPath("$.instance").value("/clients/1/teste-erro"))
				.andExpect(jsonPath("$.length()").value(5));
	}

	@Test
	@DisplayName("CT-092 — Erro interno sem detalhes técnicos")
	void naoDeveExporDetalhesTecnicosNoErroInterno() throws Exception {
		ThrowingController.excecao = new IllegalStateException("SELECT * FROM client");

		String corpo = mvc.perform(chamarRotaDeTeste())
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Erro interno"))
				.andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado."))
				.andReturn().getResponse().getContentAsString();

		assertThat(corpo).doesNotContain("SELECT", "Exception", "at br.com.", "FROM client");
	}

	@Test
	@DisplayName("CT-093 — Erros de validação listam os campos")
	void deveListarOsCamposInvalidos() throws Exception {
		mvc.perform(busca("{\"question\":\"\",\"topK\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Requisição inválida"))
				.andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos."))
				.andExpect(jsonPath("$.errors.length()").value(2))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("question", "topK")))
				.andExpect(jsonPath("$.errors[?(@.field == 'question')].message").value("não deve estar em branco"))
				.andExpect(jsonPath("$.errors[?(@.field == 'topK')].message").value("deve ser no mínimo 1"));
	}

	private static RequestBuilder chamarRotaDeTeste() {
		return get(ROTA_DE_TESTE, CLIENTE_A).header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A);
	}

	private static RequestBuilder busca(String corpo) {
		return post("/clients/{clientId}/search", CLIENTE_A)
				.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpo);
	}

	/**
	 * Controller só de teste: lança a exceção definida pelo teste. Por ser classe interna de uma classe de teste, a
	 * varredura de componentes a ignora; entra só neste contexto pelo {@code @Import}.
	 */
	@RestController
	static class ThrowingController {

		static volatile RuntimeException excecao;

		@GetMapping(ROTA_DE_TESTE)
		String lancar(@PathVariable Long clientId) {
			throw excecao;
		}

	}

}
