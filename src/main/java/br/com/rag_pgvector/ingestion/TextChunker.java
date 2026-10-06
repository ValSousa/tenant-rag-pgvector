package br.com.rag_pgvector.ingestion;

import java.util.ArrayList;
import java.util.List;

import br.com.rag_pgvector.config.RagProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

/**
 * Divide o texto em chunks com o {@code DocumentSplitters.recursive(chunkSize, chunkOverlap)} do LangChain4j, em
 * caracteres (ADR-007): parágrafo, depois linha, frase, palavra e caractere, até caber no tamanho. Antes,
 * normaliza espaços e quebras de linha repetidos; depois descarta trechos em branco e numera a partir de 0.
 */
@Component
public class TextChunker {

	private final DocumentSplitter splitter;

	public TextChunker(RagProperties ragProperties) {
		if (ragProperties.chunkOverlap() >= ragProperties.chunkSize()) {
			throw new IllegalArgumentException("app.rag.chunk-overlap (%d) deve ser menor que app.rag.chunk-size (%d)"
					.formatted(ragProperties.chunkOverlap(), ragProperties.chunkSize()));
		}
		this.splitter = DocumentSplitters.recursive(ragProperties.chunkSize(), ragProperties.chunkOverlap());
	}

	public List<Chunk> split(String text) {
		String normalized = normalize(text);
		if (normalized.isEmpty()) {
			return List.of();
		}
		List<Chunk> chunks = new ArrayList<>();
		for (TextSegment segment : splitter.split(Document.from(normalized))) {
			String content = segment.text().strip();
			if (!content.isEmpty()) {
				chunks.add(new Chunk(chunks.size(), content));
			}
		}
		return List.copyOf(chunks);
	}

	/**
	 * Espaços e tabulações repetidos viram um espaço; espaços nas pontas das linhas saem; três ou mais quebras de linha
	 * viram uma linha em branco (mantém a separação de parágrafos, que o splitter usa).
	 */
	static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return text
				.replace("\r\n", "\n")
				.replace('\r', '\n')
				.replaceAll("[ \\t\\f\\u000B\\u00A0]+", " ")
				.replaceAll(" ?\\n ?", "\n")
				.replaceAll("\\n{3,}", "\n\n")
				.strip();
	}

}
