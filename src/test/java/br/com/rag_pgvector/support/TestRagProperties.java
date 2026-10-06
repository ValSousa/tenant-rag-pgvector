package br.com.rag_pgvector.support;

import br.com.rag_pgvector.config.RagProperties;

/**
 * Fábrica de {@link RagProperties} com os valores padrão (768, 1000, 200, 5, 8, 20), para os testes não dependerem da
 * ordem do construtor (docs/QA/02, seção 2).
 */
public final class TestRagProperties {

	public static final int CHUNK_SIZE = 1000;
	public static final int CHUNK_OVERLAP = 200;
	public static final int DEFAULT_TOP_K = 5;
	public static final int ANSWER_TOP_K = 8;
	public static final int MAX_TOP_K = 20;

	private TestRagProperties() {
	}

	public static RagProperties padrao() {
		return comDimensao(Fixtures.DIMENSAO_EMBEDDING);
	}

	public static RagProperties comDimensao(int dimensao) {
		return new RagProperties(dimensao, CHUNK_SIZE, CHUNK_OVERLAP, DEFAULT_TOP_K, ANSWER_TOP_K, MAX_TOP_K);
	}

	public static RagProperties comChunk(int tamanho, int sobreposicao) {
		return new RagProperties(Fixtures.DIMENSAO_EMBEDDING, tamanho, sobreposicao, DEFAULT_TOP_K, ANSWER_TOP_K,
				MAX_TOP_K);
	}

}
