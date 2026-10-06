package br.com.rag_pgvector.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

/**
 * {@link EmbeddingModel} determinístico, sem rede, no lugar da OpenAI nos testes (docs/QA/03, seção 3.2).
 * Mesmo texto → mesmo vetor (derivado do SHA-256 do texto, normalizado). {@link #fixo(String)} dá vetores unitários
 * em eixos próprios: rótulos diferentes são ortogonais entre si.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

	private final Map<String, Embedding> registrados = new HashMap<>();
	private final Map<String, Integer> eixos = new HashMap<>();
	private RuntimeException proximaFalha;
	private int chamadas;

	@Override
	public synchronized Response<List<Embedding>> embedAll(List<TextSegment> segments) {
		chamadas++;
		if (proximaFalha != null) {
			RuntimeException falha = proximaFalha;
			proximaFalha = null;
			throw falha;
		}
		return Response.from(segments.stream()
				.map(segment -> registrados.getOrDefault(segment.text(), porHash(segment.text())))
				.toList());
	}

	@Override
	public int dimension() {
		return Fixtures.DIMENSAO_EMBEDDING;
	}

	/** Vetor unitário no eixo do rótulo: similaridade 1 consigo mesmo e 0 com outros rótulos. */
	public synchronized Embedding fixo(String rotulo) {
		int eixo = eixos.computeIfAbsent(rotulo, r -> eixos.size());
		if (eixo >= Fixtures.DIMENSAO_EMBEDDING) {
			throw new IllegalStateException("Rótulos demais para a dimensão do vetor");
		}
		float[] vetor = new float[Fixtures.DIMENSAO_EMBEDDING];
		vetor[eixo] = 1f;
		return Embedding.from(vetor);
	}

	/** Faz o texto devolver o vetor dado (usado para a pergunta). */
	public synchronized void registrar(String texto, Embedding vetor) {
		registrados.put(texto, vetor);
	}

	/** A próxima chamada lança a exceção. */
	public synchronized void falharNaProxima(RuntimeException falha) {
		proximaFalha = falha;
	}

	public synchronized int chamadas() {
		return chamadas;
	}

	public synchronized void reset() {
		registrados.clear();
		eixos.clear();
		proximaFalha = null;
		chamadas = 0;
	}

	private static Embedding porHash(String texto) {
		long semente = ByteBuffer.wrap(sha256(texto)).getLong();
		SplittableRandom random = new SplittableRandom(semente);
		float[] vetor = new float[Fixtures.DIMENSAO_EMBEDDING];
		for (int i = 0; i < vetor.length; i++) {
			vetor[i] = (float) random.nextDouble(-1, 1);
		}
		Embedding embedding = Embedding.from(vetor);
		embedding.normalize();
		return embedding;
	}

	private static byte[] sha256(String texto) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
