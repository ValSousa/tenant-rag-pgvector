package br.com.rag_pgvector.service;

import java.util.List;

import br.com.rag_pgvector.config.RagProperties;
import br.com.rag_pgvector.exception.AiProviderException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Fachada sobre o {@link EmbeddingModel} do LangChain4j, usada na ingestão e na busca para que chunks e
 * perguntas usem sempre o mesmo modelo (RN-01). Rejeita texto vazio sem chamar o modelo, confere a dimensão do vetor
 * e converte falhas do provedor em {@link AiProviderException}.
 */
@Service
public class EmbeddingService {

	private final EmbeddingModel embeddingModel;
	private final int expectedDimension;

	public EmbeddingService(EmbeddingModel embeddingModel, RagProperties ragProperties) {
		this.embeddingModel = embeddingModel;
		this.expectedDimension = ragProperties.embeddingDimension();
	}

	public Embedding embedQuery(String text) {
		requireText(text);
		Embedding embedding;
		try {
			embedding = embeddingModel.embed(text).content();
		}
		catch (RuntimeException ex) {
			throw providerFailure(ex);
		}
		return checkDimension(embedding);
	}

	/** Gera os embeddings de todos os segmentos numa única chamada ao modelo, na mesma ordem. */
	public List<Embedding> embedDocuments(List<TextSegment> segments) {
		if (segments == null || segments.isEmpty()) {
			throw new IllegalArgumentException("A lista de segmentos não pode ser vazia.");
		}
		segments.forEach(segment -> requireText(segment == null ? null : segment.text()));
		List<Embedding> embeddings;
		try {
			embeddings = embeddingModel.embedAll(segments).content();
		}
		catch (RuntimeException ex) {
			throw providerFailure(ex);
		}
		if (embeddings == null || embeddings.size() != segments.size()) {
			throw new AiProviderException("O modelo de embeddings devolveu %d vetores para %d segmentos."
					.formatted(embeddings == null ? 0 : embeddings.size(), segments.size()));
		}
		embeddings.forEach(this::checkDimension);
		return embeddings;
	}

	private static void requireText(String text) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("O texto para gerar o embedding não pode ser vazio.");
		}
	}

	private Embedding checkDimension(Embedding embedding) {
		if (embedding == null || embedding.dimension() != expectedDimension) {
			throw new AiProviderException("A dimensão do modelo (%s) não confere com a do banco (%d)."
					.formatted(embedding == null ? "nenhuma" : embedding.dimension(), expectedDimension));
		}
		return embedding;
	}

	private static AiProviderException providerFailure(RuntimeException ex) {
		return new AiProviderException("Falha ao gerar embedding no provedor de IA: " + ex.getMessage(), ex);
	}

}
