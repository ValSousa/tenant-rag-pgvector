package br.com.rag_pgvector.service;

import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import br.com.rag_pgvector.dto.SearchResponseDTO;
import br.com.rag_pgvector.dto.SearchResultDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.exception.AiProviderException;
import br.com.rag_pgvector.repository.ChunkRepository;
import br.com.rag_pgvector.support.DdtValores;
import br.com.rag_pgvector.support.TestRagProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

	private static final String PERGUNTA = "Qual é o valor da franquia da apólice?";
	private static final Embedding VETOR_PERGUNTA = Embedding.from(new float[] {1, 0, 0});

	@Mock
	EmbeddingService embeddingService;

	@Mock
	ChunkRepository chunkRepository;

	SearchService service;

	@BeforeEach
	void criarService() {
		service = new SearchService(embeddingService, chunkRepository, TestRagProperties.padrao());
	}

	@Test
	@DisplayName("CT-060 — Converte os chunks encontrados em resultados, na ordem do repository")
	void deveConverterOsChunksEmResultadosNaMesmaOrdem() {
		UUID franquia = UUID.randomUUID();
		UUID vidros = UUID.randomUUID();
		when(embeddingService.embedQuery(PERGUNTA)).thenReturn(VETOR_PERGUNTA);
		when(chunkRepository.searchByClient(CLIENTE_A, VETOR_PERGUNTA, 5)).thenReturn(List.of(
				match(franquia, 0.97, "A franquia é de R$ 3.500,00.", 10L, "contrato.pdf", "CONTRACT", 3),
				match(vidros, 0.61, "Cobertura de vidros.", 11L, "vistoria.pdf", "INSPECTION", 0)));

		SearchResponseDTO resposta = service.search(CLIENTE_A, PERGUNTA, null);

		assertThat(resposta.clientId()).isEqualTo(CLIENTE_A);
		assertThat(resposta.question()).isEqualTo(PERGUNTA);
		assertThat(resposta.results()).containsExactly(
				new SearchResultDTO(franquia, 10L, "contrato.pdf", DocumentTypeEnum.CONTRACT, 3,
						"A franquia é de R$ 3.500,00.", 0.97),
				new SearchResultDTO(vidros, 11L, "vistoria.pdf", DocumentTypeEnum.INSPECTION, 0,
						"Cobertura de vidros.", 0.61));
	}

	/** Linhas de {@code topk-validation.csv} que chegam ao service (as de 400 param no controller). */
	static Stream<Arguments> topKAceitos() throws IOException {
		try (var leitor = new BufferedReader(new InputStreamReader(
				SearchServiceTest.class.getResourceAsStream("/ddt/topk-validation.csv"), StandardCharsets.UTF_8))) {
			return leitor.lines().skip(1)
					.map(linha -> linha.split(";"))
					.filter(colunas -> "200".equals(colunas[2]))
					.map(colunas -> arguments(colunas[0], DdtValores.resolver(colunas[1]),
							Integer.parseInt(colunas[3])))
					.toList().stream();
		}
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("topKAceitos")
	@DisplayName("CT-061 — O repository recebe o topK pedido, ou 5 quando ausente (DDT)")
	void deveRepassarOTopKOuOPadrao(String cenario, String topK, int topKRepassado) {
		when(embeddingService.embedQuery(PERGUNTA)).thenReturn(VETOR_PERGUNTA);
		when(chunkRepository.searchByClient(anyLong(), any(), anyInt())).thenReturn(List.of());

		service.search(CLIENTE_A, PERGUNTA, topK == null ? null : Integer.valueOf(topK));

		verify(chunkRepository).searchByClient(CLIENTE_A, VETOR_PERGUNTA, topKRepassado);
	}

	@Test
	@DisplayName("CT-061 — topK acima do máximo é limitado a 20 no service")
	void deveLimitarOTopKAoMaximo() {
		when(embeddingService.embedQuery(PERGUNTA)).thenReturn(VETOR_PERGUNTA);
		when(chunkRepository.searchByClient(anyLong(), any(), anyInt())).thenReturn(List.of());

		service.search(CLIENTE_A, PERGUNTA, 50);

		verify(chunkRepository).searchByClient(CLIENTE_A, VETOR_PERGUNTA, 20);
	}

	@Test
	@DisplayName("CT-064 — Falha no embedding da pergunta não consulta o banco")
	void naoDeveBuscarQuandoOEmbeddingFalha() {
		when(embeddingService.embedQuery(PERGUNTA)).thenThrow(new AiProviderException("timeout"));

		assertThatThrownBy(() -> service.search(CLIENTE_A, PERGUNTA, 5)).isInstanceOf(AiProviderException.class);
		verify(chunkRepository, never()).searchByClient(anyLong(), any(), anyInt());
	}

	private static EmbeddingMatch<TextSegment> match(UUID id, double score, String texto, long documentId,
			String fileName, String documentType, int chunkIndex) {
		Metadata metadata = new Metadata()
				.put(ChunkRepository.CLIENT_ID, CLIENTE_A)
				.put(ChunkRepository.DOCUMENT_ID, documentId)
				.put(ChunkRepository.CHUNK_INDEX, chunkIndex)
				.put(ChunkRepository.DOCUMENT_TYPE, documentType)
				.put(ChunkRepository.FILE_NAME, fileName);
		return new EmbeddingMatch<>(score, id.toString(), VETOR_PERGUNTA, TextSegment.from(texto, metadata));
	}

}
