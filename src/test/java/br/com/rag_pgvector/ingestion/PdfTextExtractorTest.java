package br.com.rag_pgvector.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayInputStream;
import java.util.stream.Stream;

import br.com.rag_pgvector.exception.InvalidDocumentException;
import br.com.rag_pgvector.support.TestPdfFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PdfTextExtractorTest {

	private final PdfTextExtractor extractor = new PdfTextExtractor();

	@Test
	@DisplayName("CT-040 — Extração de PDF com texto")
	void deveExtrairOTextoDoPdf() {
		byte[] pdf = TestPdfFactory.comTexto("A franquia é de R$ 3.500,00.");

		String texto = extractor.extract(new ByteArrayInputStream(pdf));

		assertThat(texto).contains("A franquia é de R$ 3.500,00.");
	}

	@Test
	@DisplayName("CT-040 — Extração de PDF com várias páginas mantém a ordem do texto")
	void deveExtrairTextoDeVariasPaginasNaOrdem() {
		byte[] pdf = TestPdfFactory.comTexto("Primeiro parágrafo.", "x ".repeat(3000).strip(), "Último parágrafo.");

		String texto = extractor.extract(new ByteArrayInputStream(pdf));

		assertThat(texto.indexOf("Primeiro parágrafo.")).isNotNegative()
				.isLessThan(texto.indexOf("Último parágrafo."));
	}

	static Stream<Arguments> arquivosIlegiveis() {
		return Stream.of(
				arguments("PDF corrompido", TestPdfFactory.corrompido()),
				arguments("arquivo vazio", TestPdfFactory.vazio()),
				arguments("arquivo de texto", "não sou um PDF".getBytes()));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("arquivosIlegiveis")
	@DisplayName("CT-041 — PDF corrompido ou vazio")
	void naoDeveExtrairDePdfIlegivel(String cenario, byte[] arquivo) {
		assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(arquivo)))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessage("Não foi possível ler o arquivo PDF.");
	}

	@Test
	@DisplayName("CT-042 — PDF sem texto extraível")
	void naoDeveAceitarPdfSemTexto() {
		byte[] pdf = TestPdfFactory.semTexto();

		assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(pdf)))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessage("O documento não contém texto extraível.");
	}

}
