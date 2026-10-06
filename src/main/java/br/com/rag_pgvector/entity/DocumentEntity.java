package br.com.rag_pgvector.entity;

import java.time.Instant;

import br.com.rag_pgvector.enums.DocumentTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Documento original enviado pelo cliente. Os chunks não são entidade JPA: ficam em document_chunk,
 * gravados pelo PgVectorEmbeddingStore (ADR-004).
 */
@Entity
@Table(name = "document")
public class DocumentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false)
	private ClientEntity client;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 100)
	private DocumentTypeEnum documentType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentEntity() {
	}

	public DocumentEntity(ClientEntity client, String fileName, DocumentTypeEnum documentType, Instant createdAt) {
		this.client = client;
		this.fileName = fileName;
		this.documentType = documentType;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public ClientEntity getClient() {
		return client;
	}

	public String getFileName() {
		return fileName;
	}

	public DocumentTypeEnum getDocumentType() {
		return documentType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
