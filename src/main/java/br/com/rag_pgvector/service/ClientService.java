package br.com.rag_pgvector.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import br.com.rag_pgvector.dto.ClientCreatedResponseDTO;
import br.com.rag_pgvector.dto.ClientResponseDTO;
import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.exception.ResourceNotFoundException;
import br.com.rag_pgvector.repository.ClientRepository;
import br.com.rag_pgvector.security.ApiKeyHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro e consulta de clientes. A chave de API é gerada aqui e só o hash é gravado (ADR-008).
 */
@Service
public class ClientService {

	/** 32 bytes = 256 bits de entropia; em Base64 URL sem padding, 43 caracteres. */
	private static final int API_KEY_BYTES = 32;

	private final ClientRepository clientRepository;
	private final SecureRandom secureRandom = new SecureRandom();

	public ClientService(ClientRepository clientRepository) {
		this.clientRepository = clientRepository;
	}

	@Transactional
	public ClientCreatedResponseDTO create(String name) {
		String apiKey = generateApiKey();
		ClientEntity client = clientRepository.save(new ClientEntity(name, ApiKeyHasher.hash(apiKey), Instant.now()));
		return new ClientCreatedResponseDTO(client.getId(), client.getName(), apiKey);
	}

	@Transactional(readOnly = true)
	public ClientResponseDTO findById(long clientId) {
		return clientRepository.findById(clientId)
				.map(client -> new ClientResponseDTO(client.getId(), client.getName(), client.getCreatedAt()))
				.orElseThrow(() -> new ResourceNotFoundException("Cliente " + clientId + " não encontrado."));
	}

	private String generateApiKey() {
		byte[] bytes = new byte[API_KEY_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
