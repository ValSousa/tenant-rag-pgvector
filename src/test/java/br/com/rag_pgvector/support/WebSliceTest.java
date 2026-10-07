package br.com.rag_pgvector.support;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CHAVE_B;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_B;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import br.com.rag_pgvector.config.SecurityConfig;
import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.repository.ClientRepository;
import br.com.rag_pgvector.security.ApiKeyHasher;
import br.com.rag_pgvector.service.AnswerService;
import br.com.rag_pgvector.service.ClientService;
import br.com.rag_pgvector.service.DocumentService;
import br.com.rag_pgvector.service.SearchService;
import br.com.rag_pgvector.validator.DocumentFileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes de camada web (docs/QA/02, seção 4): controllers, SecurityConfig, filtro de chave e
 * GlobalExceptionHandler, com services e repositories mockados. O DocumentFileValidator, sem estado, entra real.
 */
@WebMvcTest
@Import({ SecurityConfig.class, DocumentFileValidator.class })
@ActiveProfiles("test")
public abstract class WebSliceTest {

	@Autowired
	protected MockMvc mvc;

	@MockitoBean
	protected ClientRepository clientRepository;

	@MockitoBean
	protected ClientService clientService;

	@MockitoBean
	protected DocumentService documentService;

	@MockitoBean
	protected SearchService searchService;

	@MockitoBean
	protected AnswerService answerService;

	@BeforeEach
	void chavesConhecidas() {
		when(clientRepository.findByApiKeyHash(anyString())).thenReturn(Optional.empty());
		when(clientRepository.findByApiKeyHash(ApiKeyHasher.hash(CHAVE_A))).thenReturn(Optional.of(cliente(CLIENTE_A)));
		when(clientRepository.findByApiKeyHash(ApiKeyHasher.hash(CHAVE_B))).thenReturn(Optional.of(cliente(CLIENTE_B)));
	}

	private static ClientEntity cliente(long id) {
		ClientEntity cliente = new ClientEntity("Cliente " + id, "hash-" + id, Instant.now());
		ReflectionTestUtils.setField(cliente, "id", id);
		return cliente;
	}

}
