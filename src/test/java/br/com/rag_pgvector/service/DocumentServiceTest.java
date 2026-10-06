package br.com.rag_pgvector.service;

import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import br.com.rag_pgvector.dto.DocumentResponseDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.exception.AiProviderException;
import br.com.rag_pgvector.exception.InvalidDocumentException;
import br.com.rag_pgvector.ingestion.Chunk;
import br.com.rag_pgvector.ingestion.PdfTextExtractor;
import br.com.rag_pgvector.ingestion.TextChunker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

	private static final byte[] PDF_BYTES = {'%', 'P', 'D', 'F'};
	private static final String TEXTO_CONTRATO = "A franquia é de R$ 3.500,00. A cobertura de vidros está incluída.";

	@Mock
	PdfTextExtractor pdfTextExtractor;

	@Mock
	TextChunker textChunker;

	@Mock
	EmbeddingService embeddingService;

	@Mock
	DocumentWriter documentWriter;

	@InjectMocks
	DocumentService service;

	@Captor
	ArgumentCaptor<List<TextSegment>> segments;

	@Test
	@DisplayName("CT-050 — Fluxo feliz: grava com o cliente do parâmetro, chunks na ordem e um embedding por chunk")
	void deveIngerirODocumento() {
		List<Chunk> chunks = List.of(new Chunk(0, "A franquia é de R$ 3.500,00."),
				new Chunk(1, "A cobertura de vidros está incluída."));
		List<Embedding> vetores = List.of(Embedding.from(new float[] {1, 0}), Embedding.from(new float[] {0, 1}));
		DocumentResponseDTO gravado = new DocumentResponseDTO(10L, CLIENTE_A, "contrato.pdf",
				DocumentTypeEnum.CONTRACT, Instant.now(), 2);
		when(pdfTextExtractor.extract(any())).thenReturn(TEXTO_CONTRATO);
		when(textChunker.split(TEXTO_CONTRATO)).thenReturn(chunks);
		when(embeddingService.embedDocuments(anyList())).thenReturn(vetores);
		when(documentWriter.save(CLIENTE_A, "contrato.pdf", DocumentTypeEnum.CONTRACT, chunks, vetores))
				.thenReturn(gravado);

		DocumentResponseDTO resposta = service.ingest(CLIENTE_A, "contrato.pdf", DocumentTypeEnum.CONTRACT, PDF_BYTES);

		assertThat(resposta).isSameAs(gravado);
		verify(embeddingService).embedDocuments(segments.capture());
		assertThat(segments.getValue()).extracting(TextSegment::text)
				.containsExactly("A franquia é de R$ 3.500,00.", "A cobertura de vidros está incluída.");
	}

	@Test
	@DisplayName("CT-054 — PDF ilegível ou sem texto não grava nada")
	void naoDeveGravarNadaQuandoExtracaoFalha() {
		when(pdfTextExtractor.extract(any()))
				.thenThrow(new InvalidDocumentException("O documento não contém texto extraível."));

		assertThatThrownBy(() -> service.ingest(CLIENTE_A, "contrato.pdf", DocumentTypeEnum.CONTRACT, PDF_BYTES))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessage("O documento não contém texto extraível.");

		verifyNoInteractions(textChunker, embeddingService, documentWriter);
	}

	@Test
	@DisplayName("CT-054 — Texto que não gera chunk é tratado como sem texto extraível")
	void naoDeveGravarNadaQuandoNaoHaChunks() {
		when(pdfTextExtractor.extract(any())).thenReturn("   ");
		when(textChunker.split("   ")).thenReturn(List.of());

		assertThatThrownBy(() -> service.ingest(CLIENTE_A, "contrato.pdf", DocumentTypeEnum.CONTRACT, PDF_BYTES))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessage("O documento não contém texto extraível.");

		verifyNoInteractions(embeddingService, documentWriter);
	}

	@Test
	@DisplayName("CT-055 — Falha no embedding não grava documento")
	void naoDeveGravarNadaQuandoEmbeddingFalha() {
		when(pdfTextExtractor.extract(any())).thenReturn(TEXTO_CONTRATO);
		when(textChunker.split(TEXTO_CONTRATO)).thenReturn(List.of(new Chunk(0, TEXTO_CONTRATO)));
		when(embeddingService.embedDocuments(anyList())).thenThrow(new AiProviderException("OpenAI indisponível"));

		assertThatThrownBy(() -> service.ingest(CLIENTE_A, "contrato.pdf", DocumentTypeEnum.CONTRACT, PDF_BYTES))
				.isInstanceOf(AiProviderException.class);

		verifyNoInteractions(documentWriter);
	}

}
