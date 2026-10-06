package br.com.rag_pgvector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import br.com.rag_pgvector.exception.AiProviderException;
import br.com.rag_pgvector.support.Fixtures;
import br.com.rag_pgvector.support.TestRagProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

	@Mock
	EmbeddingModel embeddingModel;

	EmbeddingService service;

	@BeforeEach
	void setUp() {
		service = new EmbeddingService(embeddingModel, TestRagProperties.padrao());
	}

	@Test
	@DisplayName("CT-030 — Embedding com a dimensão configurada")
	void deveDevolverEmbeddingComADimensaoConfigurada() {
		when(embeddingModel.embed(anyString())).thenReturn(Response.from(vetor(Fixtures.DIMENSAO_EMBEDDING)));

		Embedding embedding = service.embedQuery("franquia");

		assertThat(embedding.dimension()).isEqualTo(768);
	}

	@ParameterizedTest(name = "[{index}] texto=''{0}''")
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t", "\n"})
	@DisplayName("CT-031 — Texto vazio não chama o modelo (DDT)")
	void deveRejeitarTextoVazioSemChamarOModelo(String texto) {
		assertThatThrownBy(() -> service.embedQuery(texto))
				.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(embeddingModel);
	}

	@Test
	@DisplayName("CT-031 — Lote vazio ou com segmento em branco não chama o modelo")
	void deveRejeitarLoteVazioOuComSegmentoEmBrancoSemChamarOModelo() {
		assertThatThrownBy(() -> service.embedDocuments(List.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.embedDocuments(List.of(TextSegment.from("franquia"), TextSegment.from("  "))))
				.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(embeddingModel);
	}

	@Test
	@DisplayName("CT-032 — Dimensão diferente gera erro de configuração")
	void deveFalharQuandoDimensaoNaoConfere() {
		when(embeddingModel.embed(anyString())).thenReturn(Response.from(vetor(1536)));

		assertThatThrownBy(() -> service.embedQuery("franquia"))
				.isInstanceOf(AiProviderException.class)
				.hasMessageContaining("dimensão");
	}

	@ParameterizedTest(name = "[{index}] embedQuery com {0}")
	@MethodSource("falhasDoProvedor")
	@DisplayName("CT-033 — Falhas do provedor viram AiProviderException (DDT)")
	void deveConverterFalhaDoProvedorNaPergunta(String cenario, RuntimeException falha) {
		when(embeddingModel.embed(anyString())).thenThrow(falha);

		assertThatThrownBy(() -> service.embedQuery("franquia"))
				.isInstanceOf(AiProviderException.class)
				.hasCause(falha);
	}

	@ParameterizedTest(name = "[{index}] embedDocuments com {0}")
	@MethodSource("falhasDoProvedor")
	@DisplayName("CT-033 — Falhas do provedor viram AiProviderException nos chunks (DDT)")
	void deveConverterFalhaDoProvedorNosChunks(String cenario, RuntimeException falha) {
		when(embeddingModel.embedAll(anyList())).thenThrow(falha);

		assertThatThrownBy(() -> service.embedDocuments(List.of(TextSegment.from("franquia"))))
				.isInstanceOf(AiProviderException.class)
				.hasCause(falha);
	}

	static Stream<Arguments> falhasDoProvedor() {
		return Stream.of(
				arguments("timeout", new TimeoutException("tempo esgotado")),
				arguments("401", new AuthenticationException("chave inválida")),
				arguments("429", new RateLimitException("limite de uso atingido")),
				arguments("500", new InternalServerException("erro no servidor")));
	}

	@Test
	@DisplayName("CT-034 — Embeddings do documento em uma única chamada")
	void deveGerarEmbeddingsDoDocumentoEmUmaUnicaChamada() {
		List<TextSegment> segmentos = IntStream.range(0, 12).mapToObj(i -> TextSegment.from("trecho " + i)).toList();
		when(embeddingModel.embedAll(segmentos))
				.thenReturn(Response.from(Collections.nCopies(12, vetor(Fixtures.DIMENSAO_EMBEDDING))));

		List<Embedding> embeddings = service.embedDocuments(segmentos);

		assertThat(embeddings).hasSize(12);
		verify(embeddingModel, times(1)).embedAll(segmentos);
		verifyNoMoreInteractions(embeddingModel);
	}

	@Test
	@DisplayName("CT-034 — Quantidade de vetores diferente da de segmentos gera AiProviderException")
	void deveFalharQuandoModeloDevolveQuantidadeErradaDeVetores() {
		List<TextSegment> segmentos = List.of(TextSegment.from("trecho 0"), TextSegment.from("trecho 1"));
		when(embeddingModel.embedAll(segmentos))
				.thenReturn(Response.from(List.of(vetor(Fixtures.DIMENSAO_EMBEDDING))));

		assertThatThrownBy(() -> service.embedDocuments(segmentos))
				.isInstanceOf(AiProviderException.class);
	}

	private static Embedding vetor(int dimensao) {
		return Embedding.from(new float[dimensao]);
	}

}
