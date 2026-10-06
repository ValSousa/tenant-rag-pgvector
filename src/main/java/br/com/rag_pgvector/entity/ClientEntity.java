package br.com.rag_pgvector.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cliente (tenant). A chave de API é guardada só como hash SHA-256 em hexadecimal (ADR-008).
 */
@Entity
@Table(name = "client")
public class ClientEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
	private String apiKeyHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ClientEntity() {
	}

	public ClientEntity(String name, String apiKeyHash, Instant createdAt) {
		this.name = name;
		this.apiKeyHash = apiKeyHash;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getApiKeyHash() {
		return apiKeyHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
