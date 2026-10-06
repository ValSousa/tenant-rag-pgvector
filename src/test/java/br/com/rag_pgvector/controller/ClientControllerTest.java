package br.com.rag_pgvector.controller;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_ADMIN;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.rag_pgvector.dto.ClientCreatedResponseDTO;
import br.com.rag_pgvector.exception.ResourceNotFoundException;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.DdtValores;
import br.com.rag_pgvector.support.WebSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.http.MediaType;

class ClientControllerTest extends WebSliceTest {

	@Test
	@DisplayName("CT-020 — Administrador cadastra cliente")
	void deveCadastrarClienteComChaveDeAdministrador() throws Exception {
		when(clientService.create("Cliente A"))
				.thenReturn(new ClientCreatedResponseDTO(1L, "Cliente A", "chave-gerada-pelo-servico"));

		mvc.perform(post("/clients")
						.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_ADMIN)
						.contentType(MediaType.APPLICATION_JSON)
						.content(corpo("Cliente A")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/clients/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Cliente A"))
				.andExpect(jsonPath("$.apiKey").value(not(emptyOrNullString())));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@CsvFileSource(resources = "/ddt/client-name-validation.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-021 — Validação do nome do cliente (DDT)")
	void deveValidarNomeDoCliente(String cenario, String name, int esperado, String campoComErro) throws Exception {
		String nome = DdtValores.resolver(name);
		when(clientService.create(anyString())).thenReturn(new ClientCreatedResponseDTO(1L, nome, "chave"));

		var resultado = mvc.perform(post("/clients")
						.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_ADMIN)
						.contentType(MediaType.APPLICATION_JSON)
						.content(corpo(nome)))
				.andExpect(status().is(esperado));

		String campo = DdtValores.resolver(campoComErro);
		if (campo != null) {
			resultado
					.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Requisição inválida"))
					.andExpect(jsonPath("$.errors[*].field").value(hasItem(campo)));
			verify(clientService, never()).create(anyString());
		}
		else {
			verify(clientService).create(nome);
		}
	}

	@Test
	@DisplayName("CT-024 — Consulta de cliente inexistente pelo administrador")
	void deveResponder404ParaClienteInexistente() throws Exception {
		when(clientService.findById(999999L))
				.thenThrow(new ResourceNotFoundException("Cliente 999999 não encontrado."));

		mvc.perform(get("/clients/999999").header(ApiKeyAuthenticationFilter.HEADER, CHAVE_ADMIN))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Recurso não encontrado"))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.instance").value("/clients/999999"));
	}

	/** Corpo JSON com o nome; {@code null} gera um corpo sem o campo. */
	private static String corpo(String nome) {
		return nome == null ? "{}" : "{\"name\":\"" + nome + "\"}";
	}

}
