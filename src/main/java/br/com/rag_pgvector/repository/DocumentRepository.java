package br.com.rag_pgvector.repository;

import br.com.rag_pgvector.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

	/**
	 * Apaga os documentos do cliente com esse nome de arquivo (reenvio substitui). Os chunks saem pelo
	 * {@code ON DELETE CASCADE} da FK de {@code document_chunk}. Sempre filtrado pelo cliente: nunca afeta outro.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM DocumentEntity d WHERE d.client.id = :clientId AND d.fileName = :fileName")
	int deleteByClientIdAndFileName(@Param("clientId") long clientId, @Param("fileName") String fileName);

}
