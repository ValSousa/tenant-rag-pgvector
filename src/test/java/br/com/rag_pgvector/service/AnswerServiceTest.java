package br.com.rag_pgvector.service;

import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import br.com.rag_pgvector.dto.AnswerResponseDTO;
import br.com.rag_pgvector.dto.SearchResponseDTO;
import br.com.rag_pgvector.dto.SearchResultDTO;
import br.com.rag_pgvector.dto.SourceResponseDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.exception.AiProviderException;
import br.com.rag_pgvector.support.TestRagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

	private static final String PERGUNTA = "O sinistro pode ser considerado perda total segundo as regras da apólice?";

	@Mock
	SearchService searchService;

	@Mock
	RagAssistant ragAssistant;

	AnswerService service;

	@BeforeEach
	void criarService() {
		service = new AnswerService(searchService, ragAssistant, TestRagProperties.padrao());
	}

	@Test
	@DisplayName("CT-070 — Sem chunks, responde sem chamar o modelo")
	void deveResponderSemChamarOModeloQuandoNaoHaChunks() {
		when(searchService.search(CLIENTE_A, PERGUNTA, TestRagProperties.ANSWER_TOP_K)).thenReturn(busca(List.of()));

		AnswerResponseDTO resposta = service.answer(CLIENTE_A, PERGUNTA, null);

		assertThat(resposta.answer()).isEqualTo("Não há documentos deste cliente para responder a esta pergunta.");
		assertThat(resposta.sources()).isEmpty();
		verifyNoInteractions(ragAssistant);
	}

	@Test
	@DisplayName("CT-071 — Contexto numerado e fontes")
	void deveMontarContextoNumeradoEFontes() {
		when(searchService.search(CLIENTE_A, PERGUNTA, TestRagProperties.ANSWER_TOP_K)).thenReturn(busca(List.of(
				resultado(10L, "contrato.pdf", DocumentTypeEnum.CONTRACT, 7, "Perda total acima de 75% do valor."),
				resultado(12L, "vistoria.pdf", DocumentTypeEnum.INSPECTION, 2, "Reparo estimado em R$ 68.000,00."),
				resultado(12L, "vistoria.pdf", DocumentTypeEnum.INSPECTION, 4, "Veículo avaliado em R$ 85.000,00."))));
		when(ragAssistant.answer(anyString(), eq(PERGUNTA))).thenReturn("Sim, é perda total [1][2][3].");

		AnswerResponseDTO resposta = service.answer(CLIENTE_A, PERGUNTA, null);

		ArgumentCaptor<String> contexto = ArgumentCaptor.forClass(String.class);
		verify(ragAssistant).answer(contexto.capture(), eq(PERGUNTA));
		assertThat(contexto.getValue().lines()).containsExactly(
				"[1] (contrato.pdf, CONTRACT) Perda total acima de 75% do valor.",
				"[2] (vistoria.pdf, INSPECTION) Reparo estimado em R$ 68.000,00.",
				"[3] (vistoria.pdf, INSPECTION) Veículo avaliado em R$ 85.000,00.");
		assertThat(resposta.answer()).isEqualTo("Sim, é perda total [1][2][3].");
		assertThat(resposta.sources()).containsExactly(
				new SourceResponseDTO(1, 10L, "contrato.pdf", DocumentTypeEnum.CONTRACT, 7),
				new SourceResponseDTO(2, 12L, "vistoria.pdf", DocumentTypeEnum.INSPECTION, 2),
				new SourceResponseDTO(3, 12L, "vistoria.pdf", DocumentTypeEnum.INSPECTION, 4));
	}

	@Test
	@DisplayName("CT-072 — Falha do modelo de linguagem vira AiProviderException")
	void deveConverterFalhaDoModeloEmAiProviderException() {
		when(searchService.search(CLIENTE_A, PERGUNTA, TestRagProperties.ANSWER_TOP_K)).thenReturn(busca(List.of(
				resultado(10L, "contrato.pdf", DocumentTypeEnum.CONTRACT, 0, "Franquia de R$ 4.000,00."))));
		RuntimeException falha = new RuntimeException("HTTP 429 rate limit");
		when(ragAssistant.answer(anyString(), eq(PERGUNTA))).thenThrow(falha);

		assertThatThrownBy(() -> service.answer(CLIENTE_A, PERGUNTA, null))
				.isInstanceOf(AiProviderException.class)
				.hasCause(falha);
	}

	@Test
	@DisplayName("CT-073 — topK padrão do /ask é 8")
	void deveUsarTopK8QuandoAusente() {
		when(searchService.search(anyLong(), anyString(), any())).thenReturn(busca(List.of()));

		service.answer(CLIENTE_A, PERGUNTA, null);

		verify(searchService).search(CLIENTE_A, PERGUNTA, 8);
	}

	@Test
	@DisplayName("CT-073 — topK informado no /ask é repassado à busca")
	void deveRepassarOTopKInformado() {
		when(searchService.search(anyLong(), anyString(), any())).thenReturn(busca(List.of()));

		service.answer(CLIENTE_A, PERGUNTA, 3);

		verify(searchService).search(CLIENTE_A, PERGUNTA, 3);
	}

	private static SearchResponseDTO busca(List<SearchResultDTO> resultados) {
		return new SearchResponseDTO(CLIENTE_A, PERGUNTA, resultados);
	}

	private static SearchResultDTO resultado(long documentId, String fileName, DocumentTypeEnum tipo, int chunkIndex,
			String texto) {
		return new SearchResultDTO(UUID.randomUUID(), documentId, fileName, tipo, chunkIndex, texto, 0.9);
	}

}
