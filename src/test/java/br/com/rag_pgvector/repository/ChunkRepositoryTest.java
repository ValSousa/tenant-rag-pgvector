package br.com.rag_pgvector.repository;

import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.entity.DocumentEntity;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.ingestion.Chunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChunkRepositoryTest {

	@Mock
	EmbeddingStore<TextSegment> embeddingStore;

	@Mock
	JdbcTemplate jdbcTemplate;

	@InjectMocks
	ChunkRepository repository;

	@Captor
	ArgumentCaptor<List<String>> ids;

	@Captor
	ArgumentCaptor<List<Embedding>> embeddings;

	@Captor
	ArgumentCaptor<List<TextSegment>> segments;

	@Captor
	ArgumentCaptor<EmbeddingSearchRequest> searchRequest;

	@Test
	@DisplayName("CT-051 — saveAll grava cada chunk com os metadados do documento, num único lote")
	void deveGravarChunksComOsMetadadosDoDocumento() {
		DocumentEntity documento = documento(CLIENTE_A, 10L);
		List<Chunk> chunks = List.of(new Chunk(0, "primeiro trecho"), new Chunk(1, "segundo trecho"));
		List<Embedding> vetores = List.of(Embedding.from(new float[] {1, 0}), Embedding.from(new float[] {0, 1}));

		int gravados = repository.saveAll(CLIENTE_A, documento, chunks, vetores);

		assertThat(gravados).isEqualTo(2);
		verify(embeddingStore).addAll(ids.capture(), embeddings.capture(), segments.capture());
		assertThat(ids.getValue()).hasSize(2).doesNotHaveDuplicates()
				.allSatisfy(id -> assertThat(UUID.fromString(id)).isNotNull());
		assertThat(embeddings.getValue()).containsExactlyElementsOf(vetores);
		assertThat(segments.getValue()).extracting(TextSegment::text)
				.containsExactly("primeiro trecho", "segundo trecho");
		for (int i = 0; i < 2; i++) {
			var metadata = segments.getValue().get(i).metadata();
			assertThat(metadata.getLong("client_id")).isEqualTo(CLIENTE_A);
			assertThat(metadata.getLong("document_id")).isEqualTo(10L);
			assertThat(metadata.getInteger("chunk_index")).isEqualTo(i);
			assertThat(metadata.getString("document_type")).isEqualTo("CONTRACT");
			assertThat(metadata.getString("file_name")).isEqualTo("contrato.pdf");
			assertThat(metadata.toMap()).hasSize(5);
		}
	}

	@Test
	@DisplayName("CT-051 — saveAll recusa documento de outro cliente e listas de tamanhos diferentes")
	void naoDeveGravarComClienteDiferenteOuListasInconsistentes() {
		DocumentEntity documentoDeB = documento(CLIENTE_B, 20L);
		List<Chunk> umChunk = List.of(new Chunk(0, "trecho"));
		List<Embedding> umVetor = List.of(Embedding.from(new float[] {1}));

		assertThatThrownBy(() -> repository.saveAll(CLIENTE_A, documentoDeB, umChunk, umVetor))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> repository.saveAll(CLIENTE_B, documentoDeB, umChunk, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> repository.saveAll(CLIENTE_B, documentoDeB, List.of(), List.of()))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(embeddingStore);
	}

	@Test
	@DisplayName("CT-065 — O filtro client_id é sempre enviado ao store")
	void deveEnviarOFiltroClientIdAoStore() {
		Embedding pergunta = Embedding.from(new float[] {1, 0});
		List<EmbeddingMatch<TextSegment>> encontrados = List.of(
				new EmbeddingMatch<>(0.9, UUID.randomUUID().toString(), pergunta, TextSegment.from("trecho")));
		when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(encontrados));

		List<EmbeddingMatch<TextSegment>> resultado = repository.searchByClient(42L, pergunta, 5);

		assertThat(resultado).isEqualTo(encontrados);
		InOrder ordem = inOrder(jdbcTemplate, embeddingStore);
		ordem.verify(jdbcTemplate).execute(ChunkRepository.ITERATIVE_SCAN);
		ordem.verify(embeddingStore).search(searchRequest.capture());
		EmbeddingSearchRequest enviado = searchRequest.getValue();
		assertThat(enviado.filter()).isInstanceOfSatisfying(IsEqualTo.class, filtro -> {
			assertThat(filtro.key()).isEqualTo("client_id");
			assertThat(filtro.comparisonValue()).isEqualTo(42L);
		});
		assertThat(enviado.maxResults()).isEqualTo(5);
		assertThat(enviado.minScore()).isZero();
		assertThat(enviado.queryEmbedding()).isEqualTo(pergunta);
	}

	@Test
	@DisplayName("CT-065 — searchByClient recusa topK menor que 1 e embedding nulo sem consultar o store")
	void naoDeveBuscarComTopKInvalidoOuSemEmbedding() {
		Embedding pergunta = Embedding.from(new float[] {1, 0});

		assertThatThrownBy(() -> repository.searchByClient(CLIENTE_A, pergunta, 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> repository.searchByClient(CLIENTE_A, null, 5))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(embeddingStore, jdbcTemplate);
	}

	private static DocumentEntity documento(long clientId, long documentId) {
		ClientEntity cliente = new ClientEntity("Cliente " + clientId, "hash-" + clientId, Instant.now());
		ReflectionTestUtils.setField(cliente, "id", clientId);
		DocumentEntity documento = new DocumentEntity(cliente, "contrato.pdf", DocumentTypeEnum.CONTRACT,
				Instant.now());
		ReflectionTestUtils.setField(documento, "id", documentId);
		return documento;
	}

}
