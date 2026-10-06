package br.com.rag_pgvector.controller;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Stream;

import br.com.rag_pgvector.dto.DocumentResponseDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.TestPdfFactory;
import br.com.rag_pgvector.support.WebSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class DocumentControllerTest extends WebSliceTest {

	private static final byte[] PDF = TestPdfFactory.comTexto("A franquia é de R$ 3.500,00.");

	/** Massa da seção 3.7 do docs/QA/04 (as linhas de 201 por tipo estão no teste com @EnumSource). */
	static Stream<Arguments> uploads() {
		return Stream.of(
				arguments("PDF válido CONTRACT", arquivo(PDF, "contrato.pdf", "application/pdf"), "CONTRACT", 201),
				arguments("sem arquivo", null, "CONTRACT", 400),
				arguments("arquivo vazio", arquivo(TestPdfFactory.vazio(), "contrato.pdf", "application/pdf"),
						"CONTRACT", 400),
				arguments("arquivo .txt", arquivo("texto".getBytes(StandardCharsets.UTF_8), "contrato.txt",
						"text/plain"), "CONTRACT", 400),
				arguments("sem documentType", arquivo(PDF, "contrato.pdf", "application/pdf"), null, 400),
				arguments("documentType inválido", arquivo(PDF, "contrato.pdf", "application/pdf"), "POLICY", 400),
				arguments("documentType minúsculo", arquivo(PDF, "contrato.pdf", "application/pdf"), "contract", 400));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("uploads")
	@DisplayName("CT-052 — Validação do upload (DDT)")
	void deveValidarOUpload(String cenario, MockMultipartFile arquivo, String documentType, int esperado)
			throws Exception {
		when(documentService.ingest(anyLong(), anyString(), any(), any())).thenReturn(resposta("contrato.pdf",
				DocumentTypeEnum.CONTRACT));

		var resultado = mvc.perform(upload(CLIENTE_A, arquivo, documentType))
				.andExpect(status().is(esperado));

		if (esperado == 400) {
			resultado
					.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Requisição inválida"));
			verify(documentService, never()).ingest(anyLong(), anyString(), any(), any());
		}
		else {
			verify(documentService).ingest(eq(CLIENTE_A), eq("contrato.pdf"), eq(DocumentTypeEnum.CONTRACT), any());
		}
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@EnumSource(DocumentTypeEnum.class)
	@DisplayName("CT-052 — PDF válido é aceito para todo tipo de documento (DDT)")
	void deveAceitarPdfValidoDeTodoTipo(DocumentTypeEnum tipo) throws Exception {
		when(documentService.ingest(anyLong(), anyString(), any(), any())).thenReturn(resposta("documento.pdf", tipo));

		mvc.perform(upload(CLIENTE_A, arquivo(PDF, "documento.pdf", "application/pdf"), tipo.name()))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/clients/1/documents/10"))
				.andExpect(jsonPath("$.id").value(10))
				.andExpect(jsonPath("$.clientId").value(1))
				.andExpect(jsonPath("$.fileName").value("documento.pdf"))
				.andExpect(jsonPath("$.documentType").value(tipo.name()))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.totalChunks").value(3));

		verify(documentService).ingest(eq(CLIENTE_A), eq("documento.pdf"), eq(tipo), eq(PDF));
	}

	static Stream<Arguments> arquivosInvalidos() {
		return Stream.of(
				arguments(arquivo(TestPdfFactory.vazio(), "contrato.pdf", "application/pdf"),
						"O arquivo enviado está vazio."),
				arguments(arquivo(PDF, "", "application/pdf"), "Informe o nome do arquivo."),
				arguments(arquivo(PDF, "a".repeat(252) + ".pdf", "application/pdf"),
						"O nome do arquivo deve ter no máximo 255 caracteres."),
				arguments(arquivo("texto".getBytes(StandardCharsets.UTF_8), "contrato.txt", "text/plain"),
						"O arquivo deve ser um PDF."));
	}

	@ParameterizedTest(name = "[{index}] {1}")
	@MethodSource("arquivosInvalidos")
	@DisplayName("CT-052 — Arquivo inválido sai em 400 com a mensagem da regra (DDT)")
	void deveResponder400ComAMensagemDaRegra(MockMultipartFile arquivo, String mensagem) throws Exception {
		mvc.perform(upload(CLIENTE_A, arquivo, "CONTRACT"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Requisição inválida"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value(mensagem))
				.andExpect(jsonPath("$.instance").value("/clients/1/documents"));

		verify(documentService, never()).ingest(anyLong(), anyString(), any(), any());
	}

	@Test
	@DisplayName("CT-052 — Nome com caminho chega ao service sem o caminho")
	void deveRepassarONomeSemCaminho() throws Exception {
		when(documentService.ingest(anyLong(), anyString(), any(), any())).thenReturn(resposta("evil.pdf",
				DocumentTypeEnum.CONTRACT));

		mvc.perform(upload(CLIENTE_A, arquivo(PDF, "..\\..\\pasta\\evil.pdf", "application/pdf"), "CONTRACT"))
				.andExpect(status().isCreated());

		verify(documentService).ingest(eq(CLIENTE_A), eq("evil.pdf"), eq(DocumentTypeEnum.CONTRACT), any());
	}

	@Test
	@DisplayName("CT-058 — Parte clientId no corpo é ignorada: o dono vem do caminho")
	void deveIgnorarClientIdNoCorpo() throws Exception {
		when(documentService.ingest(anyLong(), anyString(), any(), any())).thenReturn(resposta("contrato.pdf",
				DocumentTypeEnum.CONTRACT));

		mvc.perform(upload(CLIENTE_A, arquivo(PDF, "contrato.pdf", "application/pdf"), "CONTRACT")
						.param("clientId", "2"))
				.andExpect(status().isCreated());

		verify(documentService).ingest(eq(CLIENTE_A), eq("contrato.pdf"), eq(DocumentTypeEnum.CONTRACT), any());
	}

	private static MockMultipartHttpServletRequestBuilder upload(long clientId, MockMultipartFile arquivo,
			String documentType) {
		MockMultipartHttpServletRequestBuilder request = multipart("/clients/{clientId}/documents", clientId);
		if (arquivo != null) {
			request.file(arquivo);
		}
		if (documentType != null) {
			request.param("documentType", documentType);
		}
		request.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A);
		return request;
	}

	private static MockMultipartFile arquivo(byte[] bytes, String nome, String contentType) {
		return new MockMultipartFile("file", nome, contentType, bytes);
	}

	private static DocumentResponseDTO resposta(String fileName, DocumentTypeEnum tipo) {
		return new DocumentResponseDTO(10L, CLIENTE_A, fileName, tipo, Instant.parse("2026-10-02T12:00:00Z"), 3);
	}

}
