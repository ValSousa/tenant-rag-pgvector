package br.com.rag_pgvector.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import br.com.rag_pgvector.exception.InvalidFileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** Regras do arquivo do upload, sem Spring: complementa o CT-052 do {@code DocumentControllerTest}. */
class DocumentFileValidatorTest {

	private static final byte[] CONTEUDO = "%PDF-1.4 conteúdo".getBytes(StandardCharsets.UTF_8);

	private final DocumentFileValidator validator = new DocumentFileValidator();

	static Stream<Arguments> arquivosValidos() {
		return Stream.of(
				arguments("PDF com tipo e extensão", "contrato.pdf", "application/pdf", "contrato.pdf"),
				arguments("só o content-type application/pdf", "contrato", "application/pdf", "contrato"),
				arguments("só a extensão .pdf", "contrato.pdf", "application/octet-stream", "contrato.pdf"),
				arguments("extensão .PDF maiúscula sem content-type", "CONTRATO.PDF", null, "CONTRATO.PDF"),
				arguments("content-type maiúsculo", "contrato", "APPLICATION/PDF", "contrato"),
				arguments("nome com pasta", "pasta/contrato.pdf", "application/pdf", "contrato.pdf"),
				arguments("nome com caminho relativo do Windows", "..\\..\\pasta\\evil.pdf", "application/pdf",
						"evil.pdf"),
				arguments("nome com 255 caracteres", "a".repeat(251) + ".pdf", "application/pdf",
						"a".repeat(251) + ".pdf"));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("arquivosValidos")
	@DisplayName("CT-052 — Arquivo válido devolve o nome limpo, sem caminho (DDT)")
	void deveAceitarOArquivoEDevolverONomeLimpo(String cenario, String nome, String contentType, String esperado) {
		assertThat(validator.validate(arquivo(nome, contentType, CONTEUDO))).isEqualTo(esperado);
	}

	static Stream<Arguments> arquivosInvalidos() {
		return Stream.of(
				arguments("arquivo vazio", arquivo("contrato.pdf", "application/pdf", new byte[0]),
						"O arquivo enviado está vazio."),
				arguments("nome vazio", arquivo("", "application/pdf", CONTEUDO), "Informe o nome do arquivo."),
				arguments("nome em branco", arquivo("   ", "application/pdf", CONTEUDO), "Informe o nome do arquivo."),
				arguments("só pasta, sem nome", arquivo("pasta/", "application/pdf", CONTEUDO),
						"Informe o nome do arquivo."),
				arguments("nome com 256 caracteres", arquivo("a".repeat(252) + ".pdf", "application/pdf", CONTEUDO),
						"O nome do arquivo deve ter no máximo 255 caracteres."),
				arguments("nome longo depois de tirar o caminho",
						arquivo("pasta/" + "a".repeat(252) + ".pdf", "application/pdf", CONTEUDO),
						"O nome do arquivo deve ter no máximo 255 caracteres."),
				arguments("arquivo .txt", arquivo("contrato.txt", "text/plain", CONTEUDO), "O arquivo deve ser um PDF."),
				arguments("sem extensão e sem content-type", arquivo("contrato", null, CONTEUDO),
						"O arquivo deve ser um PDF."));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("arquivosInvalidos")
	@DisplayName("CT-052 — Arquivo inválido é recusado com a mensagem da regra (DDT)")
	void naoDeveAceitarArquivoInvalido(String cenario, MultipartFile arquivo, String mensagem) {
		assertThatThrownBy(() -> validator.validate(arquivo))
				.isInstanceOf(InvalidFileException.class)
				.hasMessage(mensagem);
	}

	@Test
	@DisplayName("CT-052 — Arquivo sem nome original é recusado")
	void naoDeveAceitarArquivoSemNomeOriginal() {
		MultipartFile arquivo = mock(MultipartFile.class);
		when(arquivo.isEmpty()).thenReturn(false);
		when(arquivo.getOriginalFilename()).thenReturn(null);

		assertThatThrownBy(() -> validator.validate(arquivo))
				.isInstanceOf(InvalidFileException.class)
				.hasMessage("Informe o nome do arquivo.");
	}

	private static MultipartFile arquivo(String nome, String contentType, byte[] conteudo) {
		return new MockMultipartFile("file", nome, contentType, conteudo);
	}

}
