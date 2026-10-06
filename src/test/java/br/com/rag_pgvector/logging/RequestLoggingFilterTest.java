package br.com.rag_pgvector.logging;

import static br.com.rag_pgvector.support.LogCapturado.campo;
import static br.com.rag_pgvector.support.LogCapturado.linhasDeRequisicao;
import static br.com.rag_pgvector.support.LogCapturado.nivel;
import static br.com.rag_pgvector.support.LogCapturado.nomesDosCampos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Filtro de log por requisição sem Spring (ADR-015): campos da linha, endpoint e clientId, nível, trace ID e MDC,
 * falha na cadeia e resposta inalterada. O log é lido com o {@code OutputCaptureExtension}.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

	private static final String USER_AGENT = "AgenteRastreioLog/1.0";
	private static final String TRACE_ID = "[0-9a-f]{32}";

	private final RequestLoggingFilter filtro = new RequestLoggingFilter("tenant-rag-pgvector", "local");

	@AfterEach
	void limparMdc() {
		MDC.clear();
	}

	@Test
	@DisplayName("CT-138 — Uma linha com os dez campos por requisição")
	void deveRegistrarUmaLinhaComOsCamposDaRequisicao(CapturedOutput output) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clients/1");
		request.addHeader("User-Agent", USER_AGENT);

		executar(request, new MockHttpServletResponse(), respondendo(200));

		List<String> linhas = linhasDeRequisicao(output);
		assertThat(linhas).hasSize(1);
		String linha = linhas.getFirst();
		assertThat(nomesDosCampos(linha)).containsExactly("service", "environment", "method", "endpoint", "status",
				"clientId", "traceId", "duration");
		assertThat(campo(linha, "service")).isEqualTo("tenant-rag-pgvector");
		assertThat(campo(linha, "environment")).isEqualTo("local");
		assertThat(campo(linha, "method")).isEqualTo("GET");
		assertThat(campo(linha, "endpoint")).isEqualTo("/clients/1");
		assertThat(campo(linha, "status")).isEqualTo("200");
		assertThat(campo(linha, "clientId")).isEqualTo("1");
		assertThat(campo(linha, "traceId")).matches(TRACE_ID);
		assertThat(campo(linha, "duration")).matches("\\d+ms");
		assertThat(nivel(linha)).isEqualTo("INFO");
		assertThat(output.getAll()).doesNotContain(USER_AGENT);
	}

	static Stream<Arguments> rotas() {
		return Stream.of(
				arguments("/clients/1", null, "/clients/1", "1"),
				arguments("/clients/1/search", null, "/clients/1/search", "1"),
				arguments("/clients/42/ask", null, "/clients/42/ask", "42"),
				arguments("/clients", null, "/clients", "-"),
				arguments("/clients/abc/search", null, "/clients/abc/search", "-"),
				arguments("/actuator/env", null, "/actuator/env", "-"),
				arguments("/clients/1", "token=segredo-na-query", "/clients/1", "1"));
	}

	@ParameterizedTest(name = "[{index}] {0} ?{1} → endpoint={2} clientId={3}")
	@MethodSource("rotas")
	@DisplayName("CT-139 — endpoint sem query string e clientId tirado do caminho (DDT)")
	void deveRegistrarOCaminhoSemQueryStringEOClienteDoCaminho(String rota, String queryString,
			String endpointEsperado, String clientIdEsperado, CapturedOutput output) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", rota);
		request.setQueryString(queryString);

		executar(request, new MockHttpServletResponse(), respondendo(200));

		String linha = umaLinha(output);
		assertThat(campo(linha, "endpoint")).isEqualTo(endpointEsperado);
		assertThat(campo(linha, "clientId")).isEqualTo(clientIdEsperado);
		assertThat(output.getAll()).doesNotContain("segredo-na-query", "token=");
	}

	@Test
	@DisplayName("CT-140 — Falha na cadeia registra status=500 e a exceção continua")
	void deveRegistrarStatus500ERelancarQuandoACadeiaFalha(CapturedOutput output) {
		RuntimeException falha = new RuntimeException("falha na cadeia");
		MockFilterChain cadeia = new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req, HttpServletResponse resp) {
				throw falha;
			}
		});

		assertThatThrownBy(() -> executar(new MockHttpServletRequest("POST", "/clients/1/ask"),
				new MockHttpServletResponse(), cadeia)).isSameAs(falha);

		String linha = umaLinha(output);
		assertThat(campo(linha, "status")).isEqualTo("500");
		assertThat(campo(linha, "endpoint")).isEqualTo("/clients/1/ask");
		assertThat(nivel(linha)).isEqualTo("ERROR");
		assertThat(MDC.get(RequestLoggingFilter.TRACE_ID_MDC_KEY)).isNull();
	}

	@Test
	@DisplayName("CT-141 — traceId novo a cada requisição, no MDC só durante a requisição")
	void deveGerarUmTraceIdNovoPorRequisicaoSoDuranteARequisicao(CapturedOutput output) throws Exception {
		List<String> vistosNoMdc = new ArrayList<>();
		List<String> cabecalhos = new ArrayList<>();

		for (int i = 0; i < 2; i++) {
			MockHttpServletResponse response = new MockHttpServletResponse();
			executar(new MockHttpServletRequest("GET", "/clients/1"), response, guardandoTraceIdDoMdc(vistosNoMdc));
			cabecalhos.add(response.getHeader(RequestLoggingFilter.TRACE_ID_HEADER));
			assertThat(MDC.get(RequestLoggingFilter.TRACE_ID_MDC_KEY)).isNull();
		}

		List<String> traceIds = linhasDeRequisicao(output).stream().map(linha -> campo(linha, "traceId")).toList();
		assertThat(traceIds).hasSize(2).allMatch(traceId -> traceId.matches(TRACE_ID));
		assertThat(traceIds.get(0)).isNotEqualTo(traceIds.get(1));
		assertThat(traceIds).isEqualTo(vistosNoMdc).isEqualTo(cabecalhos);
	}

	@Test
	@DisplayName("CT-141 — Trace ID de entrada não é aceito")
	void naoDeveAceitarTraceIdDeEntrada(CapturedOutput output) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clients/1");
		request.addHeader(RequestLoggingFilter.TRACE_ID_HEADER, "abc%0AINFO falso");
		MockHttpServletResponse response = new MockHttpServletResponse();

		executar(request, response, respondendo(200));

		String traceId = campo(umaLinha(output), "traceId");
		assertThat(traceId).matches(TRACE_ID);
		assertThat(response.getHeader(RequestLoggingFilter.TRACE_ID_HEADER)).isEqualTo(traceId);
		assertThat(output.getAll()).doesNotContain("abc%0A", "INFO falso");
	}

	static Stream<Arguments> niveis() {
		return Stream.of(
				arguments(200, "INFO"), arguments(201, "INFO"), arguments(302, "INFO"),
				arguments(400, "WARN"), arguments(401, "WARN"), arguments(403, "WARN"), arguments(404, "WARN"),
				arguments(413, "WARN"),
				arguments(500, "ERROR"), arguments(503, "ERROR"));
	}

	@ParameterizedTest(name = "[{index}] status {0} → {1}")
	@MethodSource("niveis")
	@DisplayName("CT-142 — Nível do log pelo status (DDT)")
	void deveEscolherONivelPeloStatus(int status, String nivelEsperado, CapturedOutput output) throws Exception {
		executar(new MockHttpServletRequest("GET", "/clients/1"), new MockHttpServletResponse(), respondendo(status));

		String linha = umaLinha(output);
		assertThat(campo(linha, "status")).isEqualTo(String.valueOf(status));
		assertThat(nivel(linha)).isEqualTo(nivelEsperado);
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@ValueSource(strings = { "/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs" })
	@DisplayName("CT-142 — Rotas de apoio sem linha de log (DDT)")
	void naoDeveRegistrarAsRotasDeApoio(String rota, CapturedOutput output) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain cadeia = respondendo(200);

		executar(new MockHttpServletRequest("GET", rota), response, cadeia);

		assertThat(cadeia.getRequest()).as("a cadeia continua").isNotNull();
		assertThat(linhasDeRequisicao(output)).isEmpty();
		assertThat(response.getHeader(RequestLoggingFilter.TRACE_ID_HEADER)).isNull();
	}

	@Test
	@DisplayName("CT-145 — Resposta da API igual com e sem o log")
	void naoDeveAlterarAResposta() throws Exception {
		MockHttpServletResponse direta = new MockHttpServletResponse();
		respostaCriada().doFilter(new MockHttpServletRequest("POST", "/clients"), direta);

		MockHttpServletResponse comLog = new MockHttpServletResponse();
		executar(new MockHttpServletRequest("POST", "/clients"), comLog, respostaCriada());

		assertThat(comLog.getStatus()).isEqualTo(direta.getStatus()).isEqualTo(201);
		assertThat(comLog.getContentAsString()).isEqualTo(direta.getContentAsString());
		List<String> cabecalhos = new ArrayList<>(comLog.getHeaderNames());
		assertThat(cabecalhos.remove(RequestLoggingFilter.TRACE_ID_HEADER)).isTrue();
		assertThat(cabecalhos).containsExactlyInAnyOrderElementsOf(direta.getHeaderNames());
		for (String nome : direta.getHeaderNames()) {
			assertThat(comLog.getHeaders(nome)).as(nome).isEqualTo(direta.getHeaders(nome));
		}
	}

	private void executar(MockHttpServletRequest request, MockHttpServletResponse response, MockFilterChain cadeia)
			throws Exception {
		filtro.doFilter(request, response, cadeia);
	}

	private static String umaLinha(CapturedOutput output) {
		List<String> linhas = linhasDeRequisicao(output);
		assertThat(linhas).hasSize(1);
		return linhas.getFirst();
	}

	private static MockFilterChain respondendo(int status) {
		return new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req, HttpServletResponse resp) {
				resp.setStatus(status);
			}
		});
	}

	private static MockFilterChain guardandoTraceIdDoMdc(List<String> vistos) {
		return new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req, HttpServletResponse resp) {
				vistos.add(MDC.get(RequestLoggingFilter.TRACE_ID_MDC_KEY));
				resp.setStatus(200);
			}
		});
	}

	private static MockFilterChain respostaCriada() {
		return new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
				resp.setStatus(201);
				resp.setContentType("application/json");
				resp.setHeader("Location", "/clients/3");
				resp.getWriter().write("{\"id\":3,\"name\":\"Cliente C\"}");
			}
		});
	}

}
