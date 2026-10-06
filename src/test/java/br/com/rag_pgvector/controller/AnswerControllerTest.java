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

import br.com.rag_pgvector.dto.AnswerResponseDTO;
import br.com.rag_pgvector.dto.SourceResponseDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.exception.AiProviderException;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.DdtValores;
import br.com.rag_pgvector.support.WebSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AnswerControllerTest extends WebSliceTest {

	private static final String PERGUNTA = "O sinistro pode ser considerado perda total segundo as regras da apólice?";

	@Test
	@DisplayName("CT-071 — /ask responde 200 com answer e sources numeradas")
	void deveResponderComARespostaEAsFontes() throws Exception {
		when(answerService.answer(CLIENTE_A, PERGUNTA, null)).thenReturn(new AnswerResponseDTO(
				"Sim. O reparo passa de 75% do valor do veículo [1][2].",
				List.of(new SourceResponseDTO(1, 10L, "contrato.pdf", DocumentTypeEnum.CONTRACT, 7),
						new SourceResponseDTO(2, 12L, "vistoria.pdf", DocumentTypeEnum.INSPECTION, 2))));

		mvc.perform(pergunta(CLIENTE_A, corpo(PERGUNTA, null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("Sim. O reparo passa de 75% do valor do veículo [1][2]."))
				.andExpect(jsonPath("$.sources.length()").value(2))
				.andExpect(jsonPath("$.sources[0].ref").value(1))
				.andExpect(jsonPath("$.sources[0].documentId").value(10))
				.andExpect(jsonPath("$.sources[0].fileName").value("contrato.pdf"))
				.andExpect(jsonPath("$.sources[0].documentType").value("CONTRACT"))
				.andExpect(jsonPath("$.sources[0].chunkIndex").value(7))
				.andExpect(jsonPath("$.sources[1].ref").value(2))
				.andExpect(jsonPath("$.sources[1].documentType").value("INSPECTION"));
	}

	@Test
	@DisplayName("CT-072 — Falha do modelo de linguagem responde 503")
	void deveResponder503QuandoOModeloFalha() throws Exception {
		when(answerService.answer(anyLong(), anyString(), any()))
				.thenThrow(new AiProviderException("Falha ao gerar a resposta no provedor de IA: timeout"));

		mvc.perform(pergunta(CLIENTE_A, corpo(PERGUNTA, null)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Serviço de IA indisponível"))
				.andExpect(jsonPath("$.detail").value(
						"O serviço de IA está indisponível no momento. Tente novamente mais tarde."));
	}

	/** Colunas de {@code topk-validation.csv}: cenario; topK; esperado (a 4ª coluna é o padrão da busca, não do /ask). */
	@ParameterizedTest(name = "[{index}] {0}")
	@CsvFileSource(resources = "/ddt/topk-validation.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-074 — Validação do /ask: topK (DDT)")
	void deveValidarTopK(ArgumentsAccessor linha) throws Exception {
		when(answerService.answer(anyLong(), anyString(), any())).thenReturn(semDocumentos());
		String valor = DdtValores.resolver(linha.getString(1));
		int esperado = linha.getInteger(2);

		var resultado = mvc.perform(pergunta(CLIENTE_A, corpo(PERGUNTA, valor)))
				.andExpect(status().is(esperado));

		if (esperado == 400) {
			resultado
					.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Requisição inválida"));
			verify(answerService, never()).answer(anyLong(), any(), any());
		}
		else {
			// O padrão do /ask (8 quando ausente) é aplicado no AnswerService (CT-073): o controller repassa o valor
			verify(answerService).answer(CLIENTE_A, PERGUNTA, valor == null ? null : Integer.valueOf(valor));
		}
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@CsvFileSource(resources = "/ddt/question-validation.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-074 — Validação do /ask: pergunta (DDT)")
	void deveValidarAPergunta(String cenario, String question, int esperado, String campoComErro) throws Exception {
		when(answerService.answer(anyLong(), anyString(), any())).thenReturn(semDocumentos());
		String pergunta = DdtValores.resolver(question);

		var resultado = mvc.perform(pergunta(CLIENTE_A, corpo(pergunta, null)))
				.andExpect(status().is(esperado));

		String campo = DdtValores.resolver(campoComErro);
		if (campo != null) {
			resultado
					.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Requisição inválida"))
					.andExpect(jsonPath("$.errors[*].field").value(hasItem(campo)));
			verify(answerService, never()).answer(anyLong(), any(), any());
		}
		else {
			verify(answerService).answer(CLIENTE_A, pergunta, null);
		}
	}

	@Test
	@DisplayName("CT-115 (linha do RF-008) — Chave do Cliente A no /ask do Cliente B recebe 403")
	void naoDevePermitirPerguntaNoOutroCliente() throws Exception {
		mvc.perform(pergunta(CLIENTE_B, corpo(PERGUNTA, null)))
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Acesso negado"));

		verify(answerService, never()).answer(anyLong(), any(), any());
	}

	@Test
	@DisplayName("CT-080 (linha do RF-008) — /ask sem chave recebe 401")
	void naoDevePermitirPerguntaSemChave() throws Exception {
		mvc.perform(post("/clients/{clientId}/ask", CLIENTE_A)
						.contentType(MediaType.APPLICATION_JSON)
						.content(corpo(PERGUNTA, null)))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Não autenticado"));

		verify(answerService, never()).answer(anyLong(), any(), any());
	}

	private static MockHttpServletRequestBuilder pergunta(long clientId, String corpo) {
		return post("/clients/{clientId}/ask", clientId)
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

	private static AnswerResponseDTO semDocumentos() {
		return new AnswerResponseDTO("Não há documentos deste cliente para responder a esta pergunta.", List.of());
	}

}
