package br.com.rag_pgvector.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.entity.DocumentEntity;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.ingestion.Chunk;
import br.com.rag_pgvector.repository.ChunkRepository;
import br.com.rag_pgvector.repository.ClientRepository;
import br.com.rag_pgvector.repository.DocumentRepository;
import br.com.rag_pgvector.security.ApiKeyHasher;
import dev.langchain4j.data.embedding.Embedding;
import org.springframework.boot.test.context.TestComponent;

/**
 * Cria dados de teste pelo caminho real da aplicação (repositories JPA e {@link ChunkRepository}), sem SQL manual
 * (docs/QA/03, seção 3.1).
 */
@TestComponent
public class TestDataBuilder {

	private final ClientRepository clientRepository;
	private final DocumentRepository documentRepository;
	private final ChunkRepository chunkRepository;

	TestDataBuilder(ClientRepository clientRepository, DocumentRepository documentRepository,
			ChunkRepository chunkRepository) {
		this.clientRepository = clientRepository;
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
	}

	public ClientEntity cliente(String nome) {
		return clientRepository.save(new ClientEntity(nome, hashAleatorio(), Instant.now()));
	}

	/** Cliente que autentica com a chave em texto dada (ex.: {@link Fixtures#CHAVE_A}). */
	public ClientEntity cliente(String nome, String chave) {
		return clientRepository.save(new ClientEntity(nome, ApiKeyHasher.hash(chave), Instant.now()));
	}

	public DocumentEntity documento(ClientEntity cliente, String fileName, DocumentTypeEnum documentType) {
		return documentRepository.save(new DocumentEntity(cliente, fileName, documentType, Instant.now()));
	}

	/** Grava os chunks do documento pelo {@link ChunkRepository}, com índices 0..n-1 na ordem dos textos. */
	public void chunks(DocumentEntity documento, List<String> textos, List<Embedding> vetores) {
		List<Chunk> chunks = IntStream.range(0, textos.size()).mapToObj(i -> new Chunk(i, textos.get(i))).toList();
		chunkRepository.saveAll(documento.getClient().getId(), documento, chunks, vetores);
	}

	/** 64 caracteres hexadecimais, o formato de um SHA-256, sem colidir com a uk_client_api_key_hash. */
	private static String hashAleatorio() {
		return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
	}

}
