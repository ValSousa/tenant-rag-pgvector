package br.com.rag_pgvector.controller;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_B;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import br.com.rag_pgvector.dto.SearchResponseDTO;
import br.com.rag_pgvector.dto.SearchResultDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.exception.AiProviderException;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.DdtValores;
import br.com.rag_pgvector.support.WebSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class SearchControllerTest extends WebSliceTest {

	private static final String PERGUNTA = "Qual é o valor da franquia da apólice?";

	@Test
	@DisplayName("CT-060 — Busca responde 200 com os resultados do SearchService")
	void deveResponderComOsResultadosDaBusca() throws Exception {
		UUID chunkId = UUID.randomUUID();
		when(searchService.search(CLIENTE_A, PERGUNTA, 5)).thenReturn(new SearchResponseDTO(CLIENTE_A, PERGUNTA,
				List.of(new SearchResultDTO(chunkId, 10L, "contrato.pdf", DocumentTypeEnum.CONTRACT, 3,
						"A franquia é de R$ 3.500,00.", 0.9083))));

		mvc.perform(busca(CLIENTE_A, corpo(PERGUNTA, "5")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value(1))
				.andExpect(jsonPath("$.question").value(PERGUNTA))
				.andExpect(jsonPath("$.results[0].chunkId").value(chunkId.toString()))
				.andExpect(jsonPath("$.results[0].documentId").value(10))
				.andExpect(jsonPath("$.results[0].fileName").value("contrato.pdf"))
				.andExpect(jsonPath("$.results[0].documentType").value("CONTRACT"))
				.andExpect(jsonPath("$.results[0].chunkIndex").value(3))
				.andExpect(jsonPath("$.results[0].content").value("A franquia é de R$ 3.500,00."))
				.andExpect(jsonPath("$.results[0].score").value(0.9083));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@CsvFileSource(resources = "/ddt/topk-validation.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-061 — Validação e padrão de topK (DDT)")
	void deveValidarTopK(String cenario, String topK, int esperado, String topKRepassado) throws Exception {
		when(searchService.search(anyLong(), anyString(), any())).thenReturn(vazia());
		String valor = DdtValores.resolver(topK);

		var resultado = mvc.perform(busca(CLIENTE_A, corpo(PERGUNTA, valor)))
				.andExpect(status().is(esperado));

		if (esperado == 400) {
			resultado
					.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Requisição inválida"));
			verify(searchService, never()).search(anyLong(), any(), any());
		}
		else {
			verify(searchService).search(CLIENTE_A, PERGUNTA, valor == null ? null : Integer.valueOf(valor));
		}
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@CsvFileSource(resources = "/ddt/question-validation.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-062 — Validação da pergunta (DDT)")
	void deveValidarAPergunta(String cenario, String question, int esperado, String campoComErro) throws Exception {
		when(searchService.search(anyLong(), anyString(), any())).thenReturn(vazia());
		String pergunta = DdtValores.resolver(question);

		var resultado = mvc.perform(busca(CLIENTE_A, corpo(pergunta, null)))
				.andExpect(status().is(esperado));

		String campo = DdtValores.resolver(campoComErro);
		if (campo != null) {
			resultado
					.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Requisição inválida"))
					.andExpect(jsonPath("$.errors[*].field").value(hasItem(campo)));
			verify(searchService, never()).search(anyLong(), any(), any());
		}
		else {
			verify(searchService).search(CLIENTE_A, pergunta, null);
		}
	}

	@Test
	@DisplayName("CT-064 — Falha no embedding da pergunta")
	void deveResponder503QuandoOEmbeddingFalha() throws Exception {
		when(searchService.search(anyLong(), anyString(), any()))
				.thenThrow(new AiProviderException("Falha ao gerar embedding no provedor de IA: timeout"));

		mvc.perform(busca(CLIENTE_A, corpo(PERGUNTA, null)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Serviço de IA indisponível"))
				.andExpect(jsonPath("$.detail").value(
						"O serviço de IA está indisponível no momento. Tente novamente mais tarde."));
	}

	@Test
	@DisplayName("CT-115 (linha do RF-007) — Chave do Cliente A na busca do Cliente B recebe 403")
	void naoDevePermitirBuscaNoOutroCliente() throws Exception {
		mvc.perform(busca(CLIENTE_B, corpo(PERGUNTA, null)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Acesso negado"));

		verify(searchService, never()).search(anyLong(), any(), any());
	}

	@Test
	@DisplayName("CT-080 (linha do RF-007) — Busca sem chave recebe 401")
	void naoDevePermitirBuscaSemChave() throws Exception {
		mvc.perform(post("/clients/{clientId}/search", CLIENTE_A)
						.contentType(MediaType.APPLICATION_JSON)
						.content(corpo(PERGUNTA, null)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Não autenticado"));

		verify(searchService, never()).search(anyLong(), any(), any());
	}

	private static MockHttpServletRequestBuilder busca(long clientId, String corpo) {
		return post("/clients/{clientId}/search", clientId)
				.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpo);
	}

	/** Monta o JSON: campo nulo fica de fora e topK não numérico vai como texto. */
	private static String corpo(String question, String topK) {
		StringBuilder json = new StringBuilder("{");
		if (question != null) {
			json.append("\"question\":\"").append(question).append('"');
		}
		if (topK != null) {
			json.append(json.length() > 1 ? "," : "").append("\"topK\":")
					.append(topK.matches("-?\\d+") ? topK : "\"" + topK + "\"");
		}
		return json.append('}').toString();
	}

	private static SearchResponseDTO vazia() {
		return new SearchResponseDTO(CLIENTE_A, PERGUNTA, List.of());
	}

}
