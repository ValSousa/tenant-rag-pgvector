package br.com.rag_pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.temporal.ChronoUnit;

import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.entity.DocumentEntity;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.repository.ClientRepository;
import br.com.rag_pgvector.repository.DocumentRepository;
import br.com.rag_pgvector.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EntityMappingIT extends AbstractIntegrationTest {

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private DocumentRepository documentRepository;

	@Test
	@DisplayName("CT-016 — Entidades JPA gravam e leem client e document")
	void deveGravarELerClienteEDocumento() {
		ClientEntity gravado = data.cliente("Cliente A");
		DocumentEntity documento = data.documento(gravado, "vistoria.pdf", DocumentTypeEnum.INSPECTION);

		ClientEntity lido = clientRepository.findByApiKeyHash(gravado.getApiKeyHash()).orElseThrow();
		DocumentEntity documentoLido = documentRepository.findById(documento.getId()).orElseThrow();

		assertThat(lido.getId()).isEqualTo(gravado.getId());
		assertThat(lido.getName()).isEqualTo("Cliente A");
		// timestamptz guarda microssegundos e arredonda os nanossegundos (não trunca)
		assertThat(lido.getCreatedAt()).isCloseTo(gravado.getCreatedAt(), within(1, ChronoUnit.MICROS));
		assertThat(documentoLido.getClient().getId()).isEqualTo(gravado.getId());
		assertThat(documentoLido.getFileName()).isEqualTo("vistoria.pdf");
		assertThat(documentoLido.getDocumentType()).isEqualTo(DocumentTypeEnum.INSPECTION);
		assertThat(documentoLido.getCreatedAt()).isCloseTo(documento.getCreatedAt(), within(1, ChronoUnit.MICROS));
		assertThat(jdbc.queryForObject("SELECT document_type FROM document WHERE id = ?", String.class,
				documento.getId())).isEqualTo("INSPECTION");
	}

}
