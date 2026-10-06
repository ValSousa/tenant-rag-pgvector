package br.com.rag_pgvector.service;

import java.io.ByteArrayInputStream;
import java.util.List;

import br.com.rag_pgvector.dto.DocumentResponseDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.exception.InvalidDocumentException;
import br.com.rag_pgvector.ingestion.Chunk;
import br.com.rag_pgvector.ingestion.PdfTextExtractor;
import br.com.rag_pgvector.ingestion.TextChunker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Service;

/**
 * Orquestra a ingestão: extrai o texto → divide em chunks → gera os embeddings em lote → grava pelo
 * {@link DocumentWriter}. Sem transação aqui: as chamadas lentas ao modelo acontecem antes e não prendem conexão do
 * banco; se alguma falhar, nada chegou a ser gravado (arquitetura 02, seção 3.3).
 */
@Service
public class DocumentService {

	private final PdfTextExtractor pdfTextExtractor;
	private final TextChunker textChunker;
	private final EmbeddingService embeddingService;
	private final DocumentWriter documentWriter;

	public DocumentService(PdfTextExtractor pdfTextExtractor, TextChunker textChunker,
			EmbeddingService embeddingService, DocumentWriter documentWriter) {
		this.pdfTextExtractor = pdfTextExtractor;
		this.textChunker = textChunker;
		this.embeddingService = embeddingService;
		this.documentWriter = documentWriter;
	}

	/**
	 * @param clientId dono do documento, sempre o do caminho da requisição (RN-01)
	 * @param fileName nome original do arquivo (RN-05)
	 */
	public DocumentResponseDTO ingest(long clientId, String fileName, DocumentTypeEnum documentType, byte[] pdf) {
		String text = pdfTextExtractor.extract(new ByteArrayInputStream(pdf));
		List<Chunk> chunks = textChunker.split(text);
		if (chunks.isEmpty()) {
			throw new InvalidDocumentException(PdfTextExtractor.NO_EXTRACTABLE_TEXT);
		}
		List<Embedding> embeddings = embeddingService.embedDocuments(
				chunks.stream().map(chunk -> TextSegment.from(chunk.content())).toList());
		return documentWriter.save(clientId, fileName, documentType, chunks, embeddings);
	}

}
