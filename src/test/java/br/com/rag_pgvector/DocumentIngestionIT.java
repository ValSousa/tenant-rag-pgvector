package br.com.rag_pgvector;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CHAVE_B;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.AbstractIntegrationTest;
import br.com.rag_pgvector.support.TestPdfFactory;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Ingestão de ponta a ponta com banco e {@code PgVectorEmbeddingStore} reais e o FakeEmbeddingModel (docs/QA/03,
 * seção 6).
 */
class DocumentIngestionIT extends AbstractIntegrationTest {

	@BeforeEach
	void clientes() {
		data.cliente("Cliente A", CHAVE_A);
		data.cliente("Cliente B", CHAVE_B);
	}

	@Test
	@DisplayName("CT-050 — Ingestão de PDF válido")
	void deveIngerirPdfValido() throws Exception {
		String resposta = enviar(CLIENTE_A, CHAVE_A, pdf("contrato.pdf", TestPdfFactory.comTamanho(3500)), "CONTRACT")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.clientId").value(CLIENTE_A))
				.andExpect(jsonPath("$.fileName").value("contrato.pdf"))
				.andExpect(jsonPath("$.documentType").value("CONTRACT"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		long documentId = JsonPath.<Number>read(resposta, "$.id").longValue();
		int totalChunks = JsonPath.read(resposta, "$.totalChunks");

		assertThat(totalChunks).isGreaterThan(1);
		assertThat(contar("SELECT count(*) FROM document_chunk WHERE document_id = ?", documentId))
				.isEqualTo(totalChunks);
		assertThat(contar("SELECT count(*) FROM document_chunk WHERE document_id = ? AND client_id = ?"
				+ " AND embedding IS NOT NULL", documentId, CLIENTE_A)).isEqualTo(totalChunks);
		assertThat(jdbc.queryForList("SELECT chunk_index FROM document_chunk WHERE document_id = ? ORDER BY 1",
				Integer.class, documentId)).containsExactlyElementsOf(IntStream.range(0, totalChunks).boxed().toList());
		assertThat(fakeEmbeddings.chamadas()).as("embeddings do documento em uma única chamada").isEqualTo(1);
	}

	static Stream<Arguments> pdfsIlegiveis() {
		return Stream.of(
				arguments("PDF corrompido", TestPdfFactory.corrompido(), "Não foi possível ler o arquivo PDF."),
				arguments("PDF sem texto", TestPdfFactory.semTexto(), "O documento não contém texto extraível."));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("pdfsIlegiveis")
	@DisplayName("CT-054 — PDF ilegível ou sem texto não grava nada")
	void naoDeveGravarNadaComPdfIlegivel(String cenario, byte[] arquivo, String mensagem) throws Exception {
		enviar(CLIENTE_A, CHAVE_A, pdf("contrato.pdf", arquivo), "CONTRACT")
				.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Documento não processável"))
				.andExpect(jsonPath("$.detail").value(mensagem));

		assertTabelasVazias();
		assertThat(fakeEmbeddings.chamadas()).isZero();
	}

	@Test
	@DisplayName("CT-055 — Falha no embedding não grava nada")
	void naoDeveGravarNadaQuandoEmbeddingFalha() throws Exception {
		fakeEmbeddings.falharNaProxima(new RuntimeException("OpenAI indisponível"));

		enviar(CLIENTE_A, CHAVE_A, pdf("contrato.pdf", TestPdfFactory.comTamanho(3500)), "CONTRACT")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Serviço de IA indisponível"));

		assertTabelasVazias();
	}

	@Test
	@DisplayName("CT-056 — Falha ao gravar chunks desfaz o documento")
	void deveDesfazerODocumentoQuandoGravarChunksFalha() throws Exception {
		doThrow(new IllegalStateException("falha simulada ao gravar chunks"))
				.when(chunkRepository).saveAll(anyLong(), any(), anyList(), anyList());

		enviar(CLIENTE_A, CHAVE_A, pdf("contrato.pdf", TestPdfFactory.comTamanho(3500)), "CONTRACT")
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.title").value("Erro interno"));

		// O INSERT do documento foi desfeito: o store entrou na transação do JPA (TransactionAwareDataSourceProxy)
		assertTabelasVazias();
	}

	@Test
	@DisplayName("CT-056 — Falha do banco no meio do lote de chunks desfaz o documento e os chunks já gravados")
	void deveDesfazerTudoQuandoOBancoRecusaUmChunk() throws Exception {
		// Torna o INSERT do terceiro chunk inválido no banco, depois de dois chunks gravados no mesmo lote
		jdbc.execute("ALTER TABLE document_chunk ADD CONSTRAINT ck_teste_ct056 CHECK (chunk_index < 2)");
		try {
			enviar(CLIENTE_A, CHAVE_A, pdf("contrato.pdf", TestPdfFactory.comTamanho(5000)), "CONTRACT")
					.andExpect(status().isInternalServerError());
		}
		finally {
			jdbc.execute("ALTER TABLE document_chunk DROP CONSTRAINT ck_teste_ct056");
		}

		assertTabelasVazias();
	}

	@Test
	@DisplayName("CT-057 — Upload na conta de outro cliente")
	void naoDeveAceitarUploadNaContaDeOutroCliente() throws Exception {
		enviar(CLIENTE_B, CHAVE_A, pdf("contrato.pdf", TestPdfFactory.comTamanho(500)), "CONTRACT")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Acesso negado"));
		enviar(999L, CHAVE_A, pdf("contrato.pdf", TestPdfFactory.comTamanho(500)), "CONTRACT")
				.andExpect(status().isForbidden());

		assertThat(contar("SELECT count(*) FROM document WHERE client_id = ?", CLIENTE_B)).isZero();
		assertTabelasVazias();
		assertThat(fakeEmbeddings.chamadas()).isZero();
	}

	@Test
	@DisplayName("CT-058 — Dono do documento vem do caminho, data vem do sistema")
	void deveGravarDonoDoCaminhoEDataDoSistema() throws Exception {
		Instant antes = Instant.now().minusSeconds(1);

		enviar(CLIENTE_A, CHAVE_A, pdf("Apólice Original 2026.pdf", TestPdfFactory.comTamanho(500)), "CONTRACT",
				"clientId", String.valueOf(CLIENTE_B), "createdAt", "2000-01-01T00:00:00Z")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.clientId").value(CLIENTE_A));

		var documento = jdbc.queryForMap("SELECT client_id, file_name FROM document");
		assertThat(documento.get("client_id")).isEqualTo(CLIENTE_A);
		assertThat(documento.get("file_name")).isEqualTo("Apólice Original 2026.pdf");
		Instant createdAt = jdbc.queryForObject("SELECT created_at FROM document", OffsetDateTime.class).toInstant();
		assertThat(createdAt).isAfter(antes).isBeforeOrEqualTo(Instant.now());
		assertThat(contar("SELECT count(*) FROM document_chunk WHERE client_id <> ?", CLIENTE_A)).isZero();
		assertThat(contar("SELECT count(*) FROM document WHERE client_id = ?", CLIENTE_B)).isZero();
	}

	@Test
	@DisplayName("CT-059 — Reenvio do mesmo arquivo substitui o documento do cliente")
	void deveSubstituirODocumentoAoReenviarOMesmoArquivo() throws Exception {
		long antigoA = idDe(enviar(CLIENTE_A, CHAVE_A, pdf("contrato.pdf",
				TestPdfFactory.comTexto("Versão antiga: franquia de R$ 3.000,00.")), "CONTRACT"));
		long contratoB = idDe(enviar(CLIENTE_B, CHAVE_B, pdf("contrato.pdf",
				TestPdfFactory.comTexto("Contrato do Cliente B: franquia de R$ 5.500,00.")), "CONTRACT"));
		List<String> chunksDeBAntes = textosDosChunks(contratoB);

		long novoA = idDe(enviar(CLIENTE_A, CHAVE_A, pdf("contrato.pdf",
				TestPdfFactory.comTexto("Versão nova: franquia de R$ 4.000,00.")), "CONTRACT"));

		assertThat(novoA).isNotEqualTo(antigoA);
		assertThat(jdbc.queryForList("SELECT id FROM document WHERE client_id = ? AND file_name = 'contrato.pdf'",
				Long.class, CLIENTE_A)).containsExactly(novoA);
		assertThat(textosDosChunks(novoA)).isNotEmpty()
				.allSatisfy(texto -> assertThat(texto).contains("Versão nova").doesNotContain("Versão antiga"));
		assertThat(contar("SELECT count(*) FROM document_chunk WHERE document_id = ?", antigoA)).isZero();
		assertThat(contar("SELECT count(*) FROM document_chunk WHERE client_id = ? AND text LIKE '%Versão antiga%'",
				CLIENTE_A)).isZero();
		// Documentos do Cliente B não mudam
		assertThat(jdbc.queryForList("SELECT id FROM document WHERE client_id = ?", Long.class, CLIENTE_B))
				.containsExactly(contratoB);
		assertThat(textosDosChunks(contratoB)).isEqualTo(chunksDeBAntes);
	}

	@Test
	@DisplayName("CT-059 — Reenvio que falha ao gravar mantém o documento anterior")
	void deveManterODocumentoAnteriorQuandoOReenvioFalha() throws Exception {
		long antigoA = idDe(enviar(CLIENTE_A, CHAVE_A, pdf("contrato.pdf",
				TestPdfFactory.comTexto("Versão antiga: franquia de R$ 3.000,00.")), "CONTRACT"));
		List<String> chunksAntes = textosDosChunks(antigoA);
		doThrow(new IllegalStateException("falha simulada ao gravar chunks"))
				.when(chunkRepository).saveAll(anyLong(), any(), anyList(), anyList());

		enviar(CLIENTE_A, CHAVE_A, pdf("contrato.pdf", TestPdfFactory.comTexto("Versão nova.")), "CONTRACT")
				.andExpect(status().isInternalServerError());

		assertThat(jdbc.queryForList("SELECT id FROM document WHERE client_id = ?", Long.class, CLIENTE_A))
				.containsExactly(antigoA);
		assertThat(textosDosChunks(antigoA)).isEqualTo(chunksAntes);
	}

	private ResultActions enviar(long clientId, String chave, MockMultipartFile arquivo, String documentType,
			String... partesExtras) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/clients/{clientId}/documents", clientId);
		request.file(arquivo).param("documentType", documentType)
				.header(ApiKeyAuthenticationFilter.HEADER, chave);
		for (int i = 0; i < partesExtras.length; i += 2) {
			request.param(partesExtras[i], partesExtras[i + 1]);
		}
		return mvc.perform(request);
	}

	private static MockMultipartFile pdf(String nome, byte[] bytes) {
		return new MockMultipartFile("file", nome, MediaType.APPLICATION_PDF_VALUE, bytes);
	}

	private static long idDe(ResultActions resultado) throws Exception {
		String corpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(corpo, "$.id").longValue();
	}

	private List<String> textosDosChunks(long documentId) {
		return jdbc.queryForList("SELECT text FROM document_chunk WHERE document_id = ? ORDER BY chunk_index",
				String.class, documentId);
	}

	private long contar(String sql, Object... parametros) {
		return jdbc.queryForObject(sql, Long.class, parametros);
	}

	private void assertTabelasVazias() {
		assertThat(contar("SELECT count(*) FROM document")).as("document").isZero();
		assertThat(contar("SELECT count(*) FROM document_chunk")).as("document_chunk").isZero();
	}

}
