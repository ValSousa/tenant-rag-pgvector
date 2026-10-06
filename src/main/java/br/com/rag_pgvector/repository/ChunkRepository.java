package br.com.rag_pgvector.repository;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.rag_pgvector.entity.DocumentEntity;
import br.com.rag_pgvector.ingestion.Chunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único componente que usa o {@link EmbeddingStore} ({@code PgVectorEmbeddingStore} sobre {@code document_chunk},
 * ADR-004). Os metadados ficam em {@code COLUMN_PER_KEY}: a chave do metadado é o nome da coluna. A busca
 * ({@link #searchByClient}) sempre filtra por {@code client_id} no próprio SQL (arquitetura 07).
 */
@Repository
public class ChunkRepository {

	public static final String CLIENT_ID = "client_id";
	public static final String DOCUMENT_ID = "document_id";
	public static final String CHUNK_INDEX = "chunk_index";
	public static final String DOCUMENT_TYPE = "document_type";
	public static final String FILE_NAME = "file_name";

	/** Varredura iterativa do HNSW, para o filtro por cliente não devolver menos de K (arquitetura 04, seção 5.3). */
	static final String ITERATIVE_SCAN = "SET LOCAL hnsw.iterative_scan = strict_order";

	private final EmbeddingStore<TextSegment> embeddingStore;
	private final JdbcTemplate jdbcTemplate;

	public ChunkRepository(EmbeddingStore<TextSegment> embeddingStore, JdbcTemplate jdbcTemplate) {
		this.embeddingStore = embeddingStore;
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Grava os chunks do documento num único lote, um embedding por chunk, na mesma ordem. O cliente é o do parâmetro
	 * e tem de ser o dono do documento (a FK composta do banco também garante isso). Participa da transação em curso
	 * pelo {@code TransactionAwareDataSourceProxy} do {@code AiConfig}.
	 *
	 * @return quantidade de chunks gravados
	 */
	public int saveAll(long clientId, DocumentEntity document, List<Chunk> chunks, List<Embedding> embeddings) {
		if (chunks.isEmpty() || chunks.size() != embeddings.size()) {
			throw new IllegalArgumentException("São necessários ao menos um chunk e um embedding por chunk "
					+ "(chunks: %d, embeddings: %d).".formatted(chunks.size(), embeddings.size()));
		}
		if (document.getId() == null || document.getClient().getId() != clientId) {
			throw new IllegalArgumentException("O documento precisa estar gravado e pertencer ao cliente " + clientId);
		}
		List<String> ids = new ArrayList<>(chunks.size());
		List<TextSegment> segments = new ArrayList<>(chunks.size());
		for (Chunk chunk : chunks) {
			ids.add(UUID.randomUUID().toString());
			segments.add(TextSegment.from(chunk.content(), metadata(clientId, document, chunk)));
		}
		embeddingStore.addAll(ids, embeddings, segments);
		return chunks.size();
	}

	/**
	 * Busca os {@code topK} chunks do cliente mais parecidos com a pergunta, do maior para o menor {@code score}. O
	 * filtro {@code client_id} é montado aqui e vira {@code WHERE client_id = ...} no SQL do store: não existe busca
	 * sem cliente. A transação é curta e só serve para o {@code SET LOCAL} valer na mesma conexão da
	 * busca (o store usa o {@code TransactionAwareDataSourceProxy}).
	 */
	@Transactional(readOnly = true)
	public List<EmbeddingMatch<TextSegment>> searchByClient(long clientId, Embedding queryEmbedding, int topK) {
		if (queryEmbedding == null || topK < 1) {
			throw new IllegalArgumentException("A busca precisa do embedding da pergunta e de topK >= 1 (topK: %d)."
					.formatted(topK));
		}
		Filter clientFilter = metadataKey(CLIENT_ID).isEqualTo(clientId);
		EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
				.queryEmbedding(queryEmbedding)
				.maxResults(topK)
				.minScore(0.0)
				.filter(clientFilter)
				.build();
		jdbcTemplate.execute(ITERATIVE_SCAN);
		return embeddingStore.search(request).matches();
	}

	private static Metadata metadata(long clientId, DocumentEntity document, Chunk chunk) {
		return new Metadata()
				.put(CLIENT_ID, clientId)
				.put(DOCUMENT_ID, document.getId())
				.put(CHUNK_INDEX, chunk.index())
				.put(DOCUMENT_TYPE, document.getDocumentType().name())
				.put(FILE_NAME, document.getFileName());
	}

}
