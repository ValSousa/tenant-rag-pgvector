package br.com.rag_pgvector.repository;

import java.util.Optional;

import br.com.rag_pgvector.entity.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<ClientEntity, Long> {

	Optional<ClientEntity> findByApiKeyHash(String apiKeyHash);

}
