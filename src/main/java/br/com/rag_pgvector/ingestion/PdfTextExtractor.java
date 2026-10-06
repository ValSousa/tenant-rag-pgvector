package br.com.rag_pgvector.ingestion;

import java.io.InputStream;

import br.com.rag_pgvector.exception.InvalidDocumentException;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import org.springframework.stereotype.Component;

/**
 * Extrai o texto de um PDF com o {@link ApachePdfBoxDocumentParser} do LangChain4j (ADR-007). Só aceita PDF
 * com texto: arquivo ilegível ou sem texto (página em branco, imagem escaneada) vira {@link InvalidDocumentException}.
 */
@Component
public class PdfTextExtractor {

	public static final String UNREADABLE_PDF = "Não foi possível ler o arquivo PDF.";
	public static final String NO_EXTRACTABLE_TEXT = "O documento não contém texto extraível.";

	private final ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();

	public String extract(InputStream pdf) {
		Document document;
		try {
			document = parser.parse(pdf);
		}
		catch (BlankDocumentException ex) {
			throw new InvalidDocumentException(NO_EXTRACTABLE_TEXT, ex);
		}
		catch (RuntimeException ex) {
			// O parser embrulha a IOException do PDFBox (arquivo vazio, corrompido ou que não é PDF)
			throw new InvalidDocumentException(UNREADABLE_PDF, ex);
		}
		String text = document.text();
		if (text == null || text.isBlank()) {
			throw new InvalidDocumentException(NO_EXTRACTABLE_TEXT);
		}
		return text;
	}

}
