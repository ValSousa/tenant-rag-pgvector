package br.com.rag_pgvector.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import br.com.rag_pgvector.support.TestPdfFactory;
import br.com.rag_pgvector.support.TestRagProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifica o contrato do {@link TextChunker} (índices, chunks não vazios, tamanho máximo, nada se perde), não o
 * algoritmo interno do {@code DocumentSplitters.recursive} (ADR-007).
 */
class TextChunkerTest {

	private final TextChunker chunker = new TextChunker(TestRagProperties.padrao());

	@Test
	@DisplayName("CT-043 — Texto curto gera um único chunk")
	void deveGerarUmUnicoChunkParaTextoCurto() {
		String texto = TestPdfFactory.textoComTamanho(300);

		List<Chunk> chunks = chunker.split(texto);

		assertThat(chunks).containsExactly(new Chunk(0, texto.strip()));
	}

	/** Massa da seção 3.5 do docs/QA/04. */
	static Stream<Arguments> textosParaChunking() {
		return Stream.of(
				arguments("texto de 1 caractere", "a"),
				arguments("texto de 999 caracteres", "a ".repeat(500).substring(0, 999)),
				arguments("texto de exatamente 1000", "a ".repeat(500)),
				arguments("texto de 1001 caracteres", "a ".repeat(501).substring(0, 1001)),
				arguments("texto longo com parágrafos", paragrafos(12, 400)),
				arguments("palavra única de 2500 caracteres", "x".repeat(2500)),
				arguments("texto com quebras repetidas", "linha\n\n\n\nlinha\n\n\nlinha"),
				arguments("texto com acentos e R$", "A franquia é de R$ 3.500,00 e a cobertura é de 75%."));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("textosParaChunking")
	@DisplayName("CT-044 — Contrato do chunking para vários tamanhos (DDT)")
	void deveCumprirOContratoDoChunking(String cenario, String texto) {
		List<Chunk> chunks = chunker.split(texto);

		assertThat(chunks).isNotEmpty();
		assertThat(chunks).extracting(Chunk::index)
				.containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
		assertThat(chunks).allSatisfy(chunk -> {
			assertThat(chunk.content()).isNotBlank();
			assertThat(chunk.content()).hasSizeLessThanOrEqualTo(TestRagProperties.CHUNK_SIZE);
		});
		assertNenhumaPalavraSePerde(texto, chunks, TestRagProperties.CHUNK_SIZE);
	}

	@Test
	@DisplayName("CT-044 — Normaliza espaços e quebras de linha repetidos")
	void deveNormalizarEspacosEQuebrasRepetidos() {
		List<Chunk> chunks = chunker.split("  linha   um\t\tcom  espaços \r\n\n\n\n linha dois  ");

		assertThat(chunks).extracting(Chunk::content).containsExactly("linha um com espaços\n\nlinha dois");
	}

	@Test
	@DisplayName("CT-044 — Texto em branco não gera chunk")
	void naoDeveGerarChunkParaTextoEmBranco() {
		assertThat(chunker.split(" \n\n\t ")).isEmpty();
		assertThat(chunker.split(null)).isEmpty();
	}

	@Test
	@DisplayName("CT-045 — Tamanho e sobreposição vêm da configuração")
	void deveUsarTamanhoESobreposicaoDaConfiguracao() {
		TextChunker configurado = new TextChunker(TestRagProperties.comChunk(500, 50));
		String texto = TestPdfFactory.textoComTamanho(3000);

		List<Chunk> chunks = configurado.split(texto);

		assertThat(chunks).hasSizeGreaterThan(6);
		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).hasSizeLessThanOrEqualTo(500));
		assertNenhumaPalavraSePerde(texto, chunks, 500);
	}

	@Test
	@DisplayName("CT-045 — Sobreposição maior ou igual ao tamanho é recusada")
	void naoDeveAceitarSobreposicaoMaiorQueOTamanho() {
		assertThatThrownBy(() -> new TextChunker(TestRagProperties.comChunk(500, 500)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("chunk-overlap");
	}

	/**
	 * Toda palavra do texto aparece em algum chunk (RF-005 CA-02). Uma palavra maior que o chunk não cabe inteira em
	 * nenhum: nesse caso, cada caractere dela tem de estar coberto por algum chunk que seja um pedaço dela.
	 */
	private static void assertNenhumaPalavraSePerde(String texto, List<Chunk> chunks, int chunkSize) {
		for (String palavra : texto.strip().split("\\s+")) {
			if (palavra.length() <= chunkSize) {
				assertThat(chunks).as("palavra '%s' em algum chunk", palavra)
						.anySatisfy(chunk -> assertThat(chunk.content()).contains(palavra));
				continue;
			}
			boolean[] coberto = new boolean[palavra.length()];
			for (Chunk chunk : chunks) {
				String pedaco = chunk.content();
				for (int i = palavra.indexOf(pedaco); i >= 0; i = palavra.indexOf(pedaco, i + 1)) {
					for (int j = i; j < i + pedaco.length(); j++) {
						coberto[j] = true;
					}
				}
			}
			assertThat(IntStream.range(0, coberto.length).allMatch(i -> coberto[i]))
					.as("palavra de %d caracteres coberta pelos chunks", palavra.length()).isTrue();
		}
	}

	/** {@code quantidade} parágrafos de cerca de {@code tamanho} caracteres, com palavras distintas. */
	private static String paragrafos(int quantidade, int tamanho) {
		return IntStream.range(0, quantidade)
				.mapToObj(p -> {
					StringBuilder paragrafo = new StringBuilder();
					for (int w = 0; paragrafo.length() < tamanho; w++) {
						paragrafo.append("p").append(p).append("palavra").append(w)
								.append(w % 9 == 8 ? ". " : " ");
					}
					return paragrafo.toString().strip();
				})
				.collect(Collectors.joining("\n\n"));
	}

}
