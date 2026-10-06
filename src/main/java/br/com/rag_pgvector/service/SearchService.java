package br.com.rag_pgvector.service;

import java.util.List;
import java.util.UUID;

import br.com.rag_pgvector.config.RagProperties;
import br.com.rag_pgvector.dto.SearchResponseDTO;
import br.com.rag_pgvector.dto.SearchResultDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.repository.ChunkRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Service;

/**
 * Busca semântica: pergunta → embedding → {@link ChunkRepository#searchByClient} (filtro {@code client_id})
 * → top-K chunks. Sem transação aqui: o embedding da pergunta é gerado sem prender conexão do banco (arquitetura 02,
 * seção 4).
 */
@Service
public class SearchService {

	private final EmbeddingService embeddingService;
	private final ChunkRepository chunkRepository;
	private final int defaultTopK;
	private final int maxTopK;

	public SearchService(EmbeddingService embeddingService, ChunkRepository chunkRepository,
			RagProperties ragProperties) {
		this.embeddingService = embeddingService;
		this.chunkRepository = chunkRepository;
		this.defaultTopK = ragProperties.defaultTopK();
		this.maxTopK = ragProperties.maxTopK();
	}

	/**
	 * @param clientId cliente dono dos documentos, sempre o do caminho já autorizado
	 * @param topK quantidade de resultados; {@code null} usa o padrão (DP-02)
	 */
	public SearchResponseDTO search(long clientId, String question, Integer topK) {
		int limit = Math.min(topK == null ? defaultTopK : topK, maxTopK);
		Embedding queryEmbedding = embeddingService.embedQuery(question);
		List<SearchResultDTO> results = chunkRepository.searchByClient(clientId, queryEmbedding, limit).stream()
				.map(SearchService::toResult)
				.toList();
		return new SearchResponseDTO(clientId, question, results);
	}

	private static SearchResultDTO toResult(EmbeddingMatch<TextSegment> match) {
		TextSegment segment = match.embedded();
		Metadata metadata = segment.metadata();
		return new SearchResultDTO(
				UUID.fromString(match.embeddingId()),
				metadata.getLong(ChunkRepository.DOCUMENT_ID),
				metadata.getString(ChunkRepository.FILE_NAME),
				DocumentTypeEnum.valueOf(metadata.getString(ChunkRepository.DOCUMENT_TYPE)),
				metadata.getInteger(ChunkRepository.CHUNK_INDEX),
				segment.text(),
				match.score());
	}

}
