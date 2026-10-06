package br.com.rag_pgvector.service;

import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import br.com.rag_pgvector.dto.DocumentResponseDTO;
import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.entity.DocumentEntity;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.ingestion.Chunk;
import br.com.rag_pgvector.repository.ChunkRepository;
import br.com.rag_pgvector.repository.ClientRepository;
import br.com.rag_pgvector.repository.DocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentWriterTest {

	@Mock
	ClientRepository clientRepository;

	@Mock
	DocumentRepository documentRepository;

	@Mock
	ChunkRepository chunkRepository;

	@InjectMocks
	DocumentWriter writer;

	@Test
	@DisplayName("CT-059 — Reenvio substitui: apaga o documento do cliente com o mesmo nome antes de gravar o novo")
	void deveApagarODocumentoAnteriorDoClienteAntesDeGravar() {
		ClientEntity cliente = new ClientEntity("Cliente A", "hash", Instant.now());
		ReflectionTestUtils.setField(cliente, "id", CLIENTE_A);
		List<Chunk> chunks = List.of(new Chunk(0, "trecho"));
		List<Embedding> vetores = List.of(Embedding.from(new float[] {1}));
		when(clientRepository.getReferenceById(CLIENTE_A)).thenReturn(cliente);
		when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> {
			DocumentEntity documento = invocation.getArgument(0);
			ReflectionTestUtils.setField(documento, "id", 11L);
			return documento;
		});
		when(chunkRepository.saveAll(eq(CLIENTE_A), any(DocumentEntity.class), eq(chunks), eq(vetores))).thenReturn(1);
		Instant antes = Instant.now().minusMillis(1);

		DocumentResponseDTO resposta = writer.save(CLIENTE_A, "contrato.pdf", DocumentTypeEnum.CONTRACT, chunks,
				vetores);

		InOrder ordem = inOrder(documentRepository, chunkRepository);
		ordem.verify(documentRepository).deleteByClientIdAndFileName(CLIENTE_A, "contrato.pdf");
		ordem.verify(documentRepository).save(any(DocumentEntity.class));
		ordem.verify(chunkRepository).saveAll(eq(CLIENTE_A), any(DocumentEntity.class), anyList(), anyList());
		assertThat(resposta.id()).isEqualTo(11L);
		assertThat(resposta.clientId()).isEqualTo(CLIENTE_A);
		assertThat(resposta.totalChunks()).isEqualTo(1);
		assertThat(resposta.createdAt()).isAfter(antes).isBeforeOrEqualTo(Instant.now());
	}

}
