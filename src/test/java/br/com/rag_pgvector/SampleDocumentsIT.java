package br.com.rag_pgvector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.com.rag_pgvector.ingestion.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Documentos de exemplo do RF-011 ({@code documents/}) lidos com o {@link PdfTextExtractor} real (docs/QA/03,
 * seção 8). Não precisa de Spring nem de banco: só o extrator e o {@code documents/gabarito.md}.
 */
class SampleDocumentsIT {

	private static final Path DOCUMENTS = Path.of("documents");
	private static final Path GABARITO = DOCUMENTS.resolve("gabarito.md");
	private static final List<String> ARQUIVOS = List.of("contrato.pdf", "sinistro.pdf", "vistoria.pdf");

	private final PdfTextExtractor extractor = new PdfTextExtractor();

	static Stream<String> pdfsDeExemplo() {
		return Stream.of("cliente-a", "cliente-b")
				.flatMap(cliente -> ARQUIVOS.stream().map(arquivo -> cliente + "/" + arquivo));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("pdfsDeExemplo")
	@DisplayName("CT-100 — PDFs de exemplo com texto (DDT)")
	void deveExtrairTextoDeCadaPdfDeExemplo(String pdf) throws IOException {
		String texto = extrair(DOCUMENTS.resolve(pdf));

		assertThat(texto).isNotBlank();
	}

	@Test
	@DisplayName("CT-101 — Sem valores cruzados entre clientes")
	void naoDeveHaverValoresCruzadosEntreClientes() throws IOException {
		Map<String, List<String>> exclusivos = valoresExclusivos();
		String textoA = textoDoCliente("cliente-a");
		String textoB = textoDoCliente("cliente-b");

		assertThat(exclusivos.get("A")).isNotEmpty();
		assertThat(exclusivos.get("B")).isNotEmpty();

		assertThat(exclusivos.get("A")).allSatisfy(valor -> assertThat(textoA).contains(valor));
		assertThat(exclusivos.get("B")).allSatisfy(valor -> assertThat(textoB).contains(valor));

		assertThat(exclusivos.get("A")).allSatisfy(valor -> assertThat(textoB).doesNotContain(valor));
		assertThat(exclusivos.get("B")).allSatisfy(valor -> assertThat(textoA).doesNotContain(valor));
	}

	@Test
	@DisplayName("CT-102 — Gabarito completo")
	void deveTerGabaritoCompleto() throws IOException {
		String gabarito = lerGabarito();

		for (String cliente : List.of("A", "B")) {
			List<String[]> respostas = linhasDeTabela(secao(gabarito, "## Respostas — Cliente " + cliente));
			assertThat(respostas).as("respostas do Cliente %s", cliente).hasSize(5);
			for (int i = 0; i < respostas.size(); i++) {
				String[] colunas = respostas.get(i);
				assertThat(colunas[0]).isEqualTo(String.valueOf(i + 1));
				assertThat(colunas[1]).as("pergunta %d", i + 1).isNotBlank();
				assertThat(colunas[2]).as("resposta %d do Cliente %s", i + 1, cliente).isNotBlank();
			}
		}

		Map<String, String[]> perdaTotal = linhasDeTabela(secao(gabarito, "## Perda total")).stream()
				.collect(Collectors.toMap(colunas -> colunas[0], colunas -> colunas));
		assertThat(perdaTotal.get("A")[1]).isEqualTo("75%");
		assertThat(perdaTotal.get("A")[3]).isEqualTo("80%");
		assertThat(perdaTotal.get("A")[4]).isEqualTo("Sim");
		assertThat(perdaTotal.get("B")[1]).isEqualTo("70%");
		assertThat(perdaTotal.get("B")[3]).isEqualTo("53,7%");
		assertThat(perdaTotal.get("B")[4]).isEqualTo("Não");
	}

	private String extrair(Path pdf) throws IOException {
		try (InputStream entrada = Files.newInputStream(pdf)) {
			return extractor.extract(entrada);
		}
	}

	private String textoDoCliente(String pasta) throws IOException {
		StringBuilder texto = new StringBuilder();
		for (String arquivo : ARQUIVOS) {
			texto.append(extrair(DOCUMENTS.resolve(pasta).resolve(arquivo))).append(' ');
		}
		return texto.toString().replaceAll("\\s+", " ");
	}

	private static Map<String, List<String>> valoresExclusivos() throws IOException {
		String secao = secao(lerGabarito(), "## Valores exclusivos");
		Map<String, List<String>> valores = new LinkedHashMap<>();
		String cliente = null;
		for (String linha : secao.lines().toList()) {
			if (linha.startsWith("### Cliente ")) {
				cliente = linha.substring("### Cliente ".length()).strip();
				valores.put(cliente, new ArrayList<>());
			}
			else if (cliente != null && linha.startsWith("- `") && linha.endsWith("`")) {
				valores.get(cliente).add(linha.substring(3, linha.length() - 1));
			}
		}
		return valores;
	}

	private static String lerGabarito() throws IOException {
		return Files.readString(GABARITO, StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	private static String secao(String markdown, String titulo) {
		int inicio = markdown.indexOf(titulo + "\n");
		assertThat(inicio).as("seção '%s' no gabarito", titulo).isNotNegative();
		int fim = markdown.indexOf("\n## ", inicio + titulo.length());
		return markdown.substring(inicio + titulo.length(), fim < 0 ? markdown.length() : fim);
	}

	private static List<String[]> linhasDeTabela(String secao) {
		List<String> linhas = secao.lines().map(String::strip).filter(linha -> linha.startsWith("|")).toList();
		return linhas.stream().skip(2)
				.map(linha -> Stream.of(linha.substring(1, linha.length() - 1).split("\\|"))
						.map(celula -> celula.strip().replace("**", "").replaceAll("\\.$", ""))
						.toArray(String[]::new))
				.toList();
	}

}
