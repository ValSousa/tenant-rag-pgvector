package br.com.rag_pgvector.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import br.com.rag_pgvector.dto.DocumentResponseDTO;
import br.com.rag_pgvector.entity.DocumentEntity;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.ingestion.Chunk;
import br.com.rag_pgvector.repository.ChunkRepository;
import br.com.rag_pgvector.repository.ClientRepository;
import br.com.rag_pgvector.repository.DocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gravação da ingestão numa única transação: apaga o documento anterior do
 * cliente com o mesmo nome, grava o {@link DocumentEntity} e os chunks. Qualquer
 * falha desfaz tudo e mantém o documento anterior. Bean separado do {@code DocumentService} porque
 * {@code @Transactional} não vale em chamada de método da própria classe.
 */
@Service
public class DocumentWriter {

	private final ClientRepository clientRepository;
	private final DocumentRepository documentRepository;
	private final ChunkRepository chunkRepository;

	public DocumentWriter(ClientRepository clientRepository, DocumentRepository documentRepository,
			ChunkRepository chunkRepository) {
		this.clientRepository = clientRepository;
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
	}

	@Transactional
	public DocumentResponseDTO save(long clientId, String fileName, DocumentTypeEnum documentType, List<Chunk> chunks,
			List<Embedding> embeddings) {
		documentRepository.deleteByClientIdAndFileName(clientId, fileName);

		Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		DocumentEntity document = documentRepository.save(
				new DocumentEntity(clientRepository.getReferenceById(clientId), fileName, documentType, createdAt));

		int totalChunks = chunkRepository.saveAll(clientId, document, chunks, embeddings);
		return new DocumentResponseDTO(document.getId(), clientId, document.getFileName(), document.getDocumentType(),
				document.getCreatedAt(), totalChunks);
	}

}
