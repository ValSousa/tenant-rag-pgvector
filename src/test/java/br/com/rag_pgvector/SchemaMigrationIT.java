package br.com.rag_pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.entity.DocumentEntity;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.support.AbstractIntegrationTest;
import br.com.rag_pgvector.support.DdtValores;
import br.com.rag_pgvector.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.dao.DataIntegrityViolationException;

class SchemaMigrationIT extends AbstractIntegrationTest {

	/** Vetor válido de 768 posições, no formato de texto do pgvector. */
	private static final String VETOR = "[" + String.join(",", Collections.nCopies(Fixtures.DIMENSAO_EMBEDDING, "0.1")) + "]";

	@Test
	@DisplayName("CT-001 — Extensão vector habilitada no banco")
	void deveTerExtensaoVectorHabilitada() {
		var extensoes = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);

		assertThat(extensoes).contains("vector");
	}

	@Test
	@DisplayName("CT-010 — Migration V1 cria tabelas e índice HNSW")
	void deveCriarTabelasEIndiceHnsw() {
		var tabelas = jdbc.queryForList(
				"SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
		var indexdef = jdbc.queryForObject(
				"SELECT indexdef FROM pg_indexes WHERE indexname = 'document_chunk_embedding_idx'", String.class);

		assertThat(tabelas).contains("client", "document", "document_chunk");
		assertThat(indexdef)
				.contains("ON public.document_chunk")
				.contains("USING hnsw")
				.contains("vector_cosine_ops");
	}

	@Test
	@DisplayName("CT-011 — Coluna de embedding com 768 dimensões")
	void deveTerColunaEmbeddingCom768Dimensoes() {
		var tipo = jdbc.queryForObject("""
				SELECT format_type(atttypid, atttypmod)
				FROM pg_attribute
				WHERE attrelid = 'document_chunk'::regclass AND attname = 'embedding'
				""", String.class);

		assertThat(tipo).isEqualTo("vector(768)");
	}

	@Test
	@DisplayName("CT-012 — Documento com cliente inexistente é rejeitado")
	void deveRejeitarDocumentoComClienteInexistente() {
		assertThatThrownBy(() -> inserirDocumento(999L, "CONTRACT"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("fk_document_client");
	}

	@Test
	@DisplayName("CT-013 — Chunk não pode ter cliente diferente do documento")
	void deveRejeitarChunkComClienteDiferenteDoDocumento() {
		ClientEntity clienteA = data.cliente("Cliente A");
		ClientEntity clienteB = data.cliente("Cliente B");
		DocumentEntity documentoA = data.documento(clienteA, "contrato.pdf", DocumentTypeEnum.CONTRACT);

		assertThatThrownBy(() -> inserirChunk(documentoA.getId(), clienteB.getId(), 0))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("fk_chunk_document_client");
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@CsvFileSource(resources = "/ddt/document-type-check.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-014 — Tipo de documento fora da lista é rejeitado pelo banco (DDT)")
	void deveAceitarSomenteTiposDaLista(String cenario, String documentType, boolean aceito) {
		long clienteA = data.cliente("Cliente A").getId();
		String tipo = DdtValores.resolver(documentType);

		if (aceito) {
			assertThatCode(() -> inserirDocumento(clienteA, tipo)).doesNotThrowAnyException();
		}
		else {
			assertThatThrownBy(() -> inserirDocumento(clienteA, tipo))
					.isInstanceOf(DataIntegrityViolationException.class)
					.hasMessageContaining("ck_document_type");
		}
	}

	@Test
	@DisplayName("CT-015 — Exclusões em cascata e bloqueadas")
	void deveApagarChunksEmCascataEBloquearExclusaoDeCliente() {
		ClientEntity clienteA = data.cliente("Cliente A");
		DocumentEntity documento = data.documento(clienteA, "contrato.pdf", DocumentTypeEnum.CONTRACT);
		for (int i = 0; i < 3; i++) {
			inserirChunk(documento.getId(), clienteA.getId(), i);
		}
		data.documento(clienteA, "sinistro.pdf", DocumentTypeEnum.CLAIM);

		jdbc.update("DELETE FROM document WHERE id = ?", documento.getId());

		assertThat(contarChunks(documento.getId())).isZero();
		assertThatThrownBy(() -> jdbc.update("DELETE FROM client WHERE id = ?", clienteA.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("fk_document_client");
	}

	private void inserirDocumento(long clientId, String documentType) {
		jdbc.update("INSERT INTO document (client_id, file_name, document_type, created_at) VALUES (?, ?, ?, ?)",
				clientId, "documento.pdf", documentType, Timestamp.from(Instant.now()));
	}

	private void inserirChunk(long documentId, long clientId, int chunkIndex) {
		jdbc.update("""
				INSERT INTO document_chunk
				    (embedding_id, embedding, text, client_id, document_id, chunk_index, document_type, file_name)
				VALUES (?, ?::vector, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), VETOR, "trecho " + chunkIndex, clientId, documentId, chunkIndex,
				"CONTRACT", "contrato.pdf");
	}

	private int contarChunks(long documentId) {
		return jdbc.queryForObject("SELECT count(*) FROM document_chunk WHERE document_id = ?", Integer.class,
				documentId);
	}

}
