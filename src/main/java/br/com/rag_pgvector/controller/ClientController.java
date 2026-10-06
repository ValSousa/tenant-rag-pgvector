package br.com.rag_pgvector.controller;

import java.net.URI;

import br.com.rag_pgvector.dto.ClientCreatedResponseDTO;
import br.com.rag_pgvector.dto.ClientResponseDTO;
import br.com.rag_pgvector.dto.CreateClientRequestDTO;
import br.com.rag_pgvector.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clients")
@Tag(name = "Clientes", description = "Cadastro e consulta de clientes")
public class ClientController {

	private final ClientService clientService;

	public ClientController(ClientService clientService) {
		this.clientService = clientService;
	}

	@PostMapping
	@Operation(summary = "Cadastra um cliente",
			description = "Somente administrador. A apiKey é mostrada só nesta resposta.")
	@ApiResponse(responseCode = "201", description = "Cliente cadastrado")
	@ApiResponse(responseCode = "400", description = "Nome vazio ou com mais de 255 caracteres")
	@ApiResponse(responseCode = "401", description = "Sem chave ou chave desconhecida")
	@ApiResponse(responseCode = "403", description = "Chave de cliente")
	public ResponseEntity<ClientCreatedResponseDTO> create(@Valid @RequestBody CreateClientRequestDTO request) {
		ClientCreatedResponseDTO created = clientService.create(request.name());
		return ResponseEntity.created(URI.create("/clients/" + created.id())).body(created);
	}

	@GetMapping("/{clientId}")
	@Operation(summary = "Consulta um cliente", description = "Administrador ou o próprio cliente.")
	@ApiResponse(responseCode = "200", description = "Cliente encontrado")
	@ApiResponse(responseCode = "401", description = "Sem chave ou chave desconhecida")
	@ApiResponse(responseCode = "403", description = "Chave de outro cliente")
	@ApiResponse(responseCode = "404", description = "Cliente inexistente (administrador)")
	public ClientResponseDTO findById(@PathVariable long clientId) {

		return clientService.findById(clientId);
	}

}
