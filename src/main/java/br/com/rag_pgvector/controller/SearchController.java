package br.com.rag_pgvector.controller;

import br.com.rag_pgvector.dto.SearchRequestDTO;
import br.com.rag_pgvector.dto.SearchResponseDTO;
import br.com.rag_pgvector.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clients/{clientId}/search")
@Tag(name = "Busca", description = "Busca semântica nos documentos do cliente")
public class SearchController {

	private final SearchService searchService;

	public SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	@PostMapping
	@Operation(summary = "Busca os chunks mais parecidos com a pergunta",
			description = "Somente o próprio cliente. Busca apenas nos documentos do cliente do caminho e devolve "
					+ "até topK chunks (padrão 5, de 1 a 20), do mais parecido para o menos parecido.")
	@ApiResponse(responseCode = "200", description = "Resultados (lista vazia se o cliente não tiver documentos)")
	@ApiResponse(responseCode = "400", description = "Pergunta vazia ou com mais de 2000 caracteres; topK fora de 1 a 20")
	@ApiResponse(responseCode = "401", description = "Sem chave ou chave desconhecida")
	@ApiResponse(responseCode = "403", description = "Chave de outro cliente")
	@ApiResponse(responseCode = "503", description = "Serviço de IA indisponível")
	public SearchResponseDTO search(@PathVariable long clientId, @Valid @RequestBody SearchRequestDTO request) {
		// O cliente vem sempre do caminho, já autorizado pelo SecurityConfig
		return searchService.search(clientId, request.question(), request.topK());
	}

}
