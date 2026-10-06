package br.com.rag_pgvector.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeEmbeddingModelTest {

	private final FakeEmbeddingModel fake = new FakeEmbeddingModel();

	@Test
	@DisplayName("CT-036 — FakeEmbeddingModel é determinístico")
	void deveSerDeterministico() {
		Embedding primeiro = fake.embed("Qual é o valor da franquia?").content();
		Embedding segundo = fake.embed("Qual é o valor da franquia?").content();
		Embedding outroTexto = fake.embed("Quando ocorreu o sinistro?").content();

		assertThat(primeiro).isEqualTo(segundo);
		assertThat(primeiro).isNotEqualTo(outroTexto);
		assertThat(primeiro.dimension()).isEqualTo(768);
		assertThat(norma(primeiro)).isCloseTo(1.0, within(1e-5));
		assertThat(fake.chamadas()).isEqualTo(3);
	}

	@Test
	@DisplayName("CT-036 — Vetor registrado com fixo() é devolvido exatamente")
	void deveDevolverOVetorRegistrado() {
		Embedding franquia = fake.fixo("franquia");
		fake.registrar("Qual é o valor da franquia?", franquia);

		Embedding devolvido = fake.embed("Qual é o valor da franquia?").content();

		assertThat(devolvido).isEqualTo(franquia);
		assertThat(fake.fixo("franquia")).isEqualTo(franquia);
		assertThat(CosineSimilarity.between(franquia, fake.fixo("vidros"))).isCloseTo(0.0, within(1e-9));
	}

	@Test
	@DisplayName("CT-036 — falharNaProxima lança uma vez e reset limpa o estado")
	void deveFalharUmaVezEResetarOEstado() {
		var falha = new IllegalStateException("OpenAI indisponível");
		fake.registrar("pergunta", fake.fixo("franquia"));
		fake.falharNaProxima(falha);

		assertThatThrownBy(() -> fake.embed("pergunta")).isSameAs(falha);
		assertThat(fake.embed("pergunta").content()).isEqualTo(fake.fixo("franquia"));

		fake.reset();

		assertThat(fake.chamadas()).isZero();
		assertThat(fake.embed("pergunta").content()).isNotEqualTo(fake.fixo("franquia"));
	}

	private static double norma(Embedding embedding) {
		double soma = 0;
		for (float valor : embedding.vector()) {
			soma += valor * valor;
		}
		return Math.sqrt(soma);
	}

}
