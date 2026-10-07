package br.com.rag_pgvector.logging;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CHAVE_ADMIN;
import static br.com.rag_pgvector.support.Fixtures.CHAVE_B;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static br.com.rag_pgvector.support.LogCapturado.campo;
import static br.com.rag_pgvector.support.LogCapturado.linhasDeRequisicao;
import static br.com.rag_pgvector.support.LogCapturado.nivel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import br.com.rag_pgvector.dto.ClientCreatedResponseDTO;
import br.com.rag_pgvector.dto.ClientResponseDTO;
import br.com.rag_pgvector.exception.ResourceNotFoundException;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.DdtValores;
import br.com.rag_pgvector.support.WebSliceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Log por requisição na fatia web (ADR-015): o filtro roda antes da cadeia do Spring Security, então as respostas
 * 401/403 também têm a linha e o cabeçalho {@code X-Trace-Id}.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingWebTest extends WebSliceTest {

	private static final Map<String, String> CHAVES = Map.of(
			"CHAVE_A", CHAVE_A,
			"CHAVE_B", CHAVE_B,
			"CHAVE_ADMIN", CHAVE_ADMIN,
			"CHAVE_INVALIDA", "chave-invalida-do-log");

	@BeforeEach
	void respostasDosServices() {
		when(clientService.findById(CLIENTE_A))
				.thenReturn(new ClientResponseDTO(CLIENTE_A, "Cliente A", Instant.parse("2026-10-01T13:45:00Z")));
		when(clientService.findById(999L)).thenThrow(new ResourceNotFoundException("Cliente 999 não encontrado"));
		when(clientService.create(anyString())).thenReturn(new ClientCreatedResponseDTO(3L, "Cliente C", "chave-c"));
	}

	@ParameterizedTest(name = "[{index}] CT-143 — {0}: {2} {3} com {1} → {4}")
	@CsvFileSource(resources = "/ddt/request-log-matrix.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-143 — Linha de log para 200, 201, 401, 403 e 404 pela API (DDT)")
	void deveRegistrarUmaLinhaPorRequisicao(String cenario, String chave, String metodo, String rota, int status,
			String endpoint, String clientId, String nivelEsperado, String naoContem, CapturedOutput output)
			throws Exception {
		MockHttpServletResponse resposta = mvc.perform(requisicao(HttpMethod.valueOf(metodo), rota, valor(chave)))
				.andReturn().getResponse();

		assertThat(resposta.getStatus()).as(cenario).isEqualTo(status);
		List<String> linhas = linhasDeRequisicao(output);
		assertThat(linhas).as(cenario).hasSize(1);
		String linha = linhas.getFirst();
		assertThat(campo(linha, "method")).isEqualTo(metodo);
		assertThat(campo(linha, "endpoint")).isEqualTo(endpoint);
		assertThat(campo(linha, "status")).isEqualTo(String.valueOf(status));
		assertThat(campo(linha, "clientId")).isEqualTo(clientId);
		assertThat(nivel(linha)).isEqualTo(nivelEsperado);
		assertThat(campo(linha, "traceId")).matches("[0-9a-f]{32}")
				.as("X-Trace-Id também nos 401/403").isEqualTo(resposta.getHeader(RequestLoggingFilter.TRACE_ID_HEADER));
		String proibido = valor(naoContem);
		if (proibido != null) {
			assertThat(output.getAll()).doesNotContain(proibido);
		}
	}

	@Test
	@DisplayName("CT-145 — Resposta da API igual com e sem o log (só o X-Trace-Id é acrescentado)")
	void naoDeveAlterarAsRespostasDaApi() throws Exception {
		mvc.perform(post("/clients").header(ApiKeyAuthenticationFilter.HEADER, CHAVE_ADMIN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Cliente C\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/clients/3"))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(3))
				.andExpect(jsonPath("$.apiKey").value("chave-c"))
				.andExpect(header().exists(RequestLoggingFilter.TRACE_ID_HEADER));

		mvc.perform(get("/clients/{id}", CLIENTE_A))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Não autenticado"))
				.andExpect(header().exists(RequestLoggingFilter.TRACE_ID_HEADER));
	}

	private static String valor(String daMassa) {
		String nome = DdtValores.resolver(daMassa);
		return nome == null ? null : CHAVES.getOrDefault(nome, nome);
	}

	private static MockHttpServletRequestBuilder requisicao(HttpMethod metodo, String rota, String chave) {
		MockHttpServletRequestBuilder builder = request(metodo, rota);
		if (rota.endsWith("/search") || rota.endsWith("/ask")) {
			builder.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Qual é o valor da franquia?\"}");
		}
		else if (HttpMethod.POST.equals(metodo)) {
			builder.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cliente C\"}");
		}
		return chave == null ? builder : builder.header(ApiKeyAuthenticationFilter.HEADER, chave);
	}

}
