package br.com.rag_pgvector.controller;

import java.io.IOException;
import java.net.URI;

import br.com.rag_pgvector.dto.DocumentResponseDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.service.DocumentService;
import br.com.rag_pgvector.validator.DocumentFileValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/clients/{clientId}/documents")
@Tag(name = "Documentos", description = "Ingestão de documentos PDF do cliente")
public class DocumentController {

	private final DocumentService documentService;

	private final DocumentFileValidator documentFileValidator;

	public DocumentController(DocumentService documentService, DocumentFileValidator documentFileValidator) {
		this.documentService = documentService;
		this.documentFileValidator = documentFileValidator;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Envia um documento PDF do cliente",
			description = "Somente o próprio cliente. Extrai o texto, divide em chunks, gera os embeddings e grava. "
					+ "Reenviar um arquivo com o mesmo nome substitui o documento anterior do cliente.")
	@ApiResponse(responseCode = "201", description = "Documento ingerido")
	@ApiResponse(responseCode = "400", description = "Arquivo ausente, vazio ou que não é PDF; tipo ausente ou inválido")
	@ApiResponse(responseCode = "401", description = "Sem chave ou chave desconhecida")
	@ApiResponse(responseCode = "403", description = "Chave de outro cliente")
	@ApiResponse(responseCode = "413", description = "Arquivo acima de 5 MB")
	@ApiResponse(responseCode = "422", description = "PDF ilegível ou sem texto extraível")
	@ApiResponse(responseCode = "503", description = "Serviço de IA indisponível")
	public ResponseEntity<DocumentResponseDTO> upload(@PathVariable long clientId,
			@RequestPart("file") MultipartFile file,
			@RequestParam("documentType") DocumentTypeEnum documentType) throws IOException {
		String fileName = documentFileValidator.validate(file);
		DocumentResponseDTO document = documentService.ingest(clientId, fileName, documentType, file.getBytes());
		return ResponseEntity.created(URI.create("/clients/" + clientId + "/documents/" + document.id()))
				.body(document);
	}

}
