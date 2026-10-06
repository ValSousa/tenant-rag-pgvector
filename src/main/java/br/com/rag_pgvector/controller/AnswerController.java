package br.com.rag_pgvector.controller;

import br.com.rag_pgvector.dto.AnswerResponseDTO;
import br.com.rag_pgvector.dto.AskRequestDTO;
import br.com.rag_pgvector.service.AnswerService;
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
@RequestMapping("/clients/{clientId}/ask")
@Tag(name = "Resposta", description = "Resposta a perguntas com base nos documentos do cliente")
public class AnswerController {

	private final AnswerService answerService;

	public AnswerController(AnswerService answerService) {
		this.answerService = answerService;
	}

	@PostMapping
	@Operation(summary = "Responde à pergunta usando só os documentos do cliente",
			description = "Somente o próprio cliente. Busca os topK trechos mais parecidos nos documentos do cliente "
					+ "do caminho (padrão 8, de 1 a 20) e gera a resposta com o modelo de linguagem, que cita os "
					+ "trechos como [n] (lista sources). Sem documentos, responde sem chamar o modelo.")
	@ApiResponse(responseCode = "200", description = "Resposta e fontes (sources vazio se o cliente não tiver documentos)")
	@ApiResponse(responseCode = "400", description = "Pergunta vazia ou com mais de 2000 caracteres; topK fora de 1 a 20")
	@ApiResponse(responseCode = "401", description = "Sem chave ou chave desconhecida")
	@ApiResponse(responseCode = "403", description = "Chave de outro cliente")
	@ApiResponse(responseCode = "503", description = "Serviço de IA indisponível")
	public AnswerResponseDTO ask(@PathVariable long clientId, @Valid @RequestBody AskRequestDTO request) {
		return answerService.answer(clientId, request.question(), request.topK());
	}

}
