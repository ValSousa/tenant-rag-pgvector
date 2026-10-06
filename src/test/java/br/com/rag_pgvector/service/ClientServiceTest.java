package br.com.rag_pgvector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import br.com.rag_pgvector.dto.ClientCreatedResponseDTO;
import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.exception.ResourceNotFoundException;
import br.com.rag_pgvector.repository.ClientRepository;
import br.com.rag_pgvector.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

	@Mock
	ClientRepository clientRepository;

	ClientService service;

	@BeforeEach
	void setUp() {
		service = new ClientService(clientRepository);
	}

	@Test
	@DisplayName("CT-026 — Chave gerada é aleatória e só o hash é guardado")
	void deveGerarChaveAleatoriaEGuardarSoOHash() {
		when(clientRepository.save(any(ClientEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
		var captor = ArgumentCaptor.forClass(ClientEntity.class);

		ClientCreatedResponseDTO primeiro = service.create("Cliente A");
		ClientCreatedResponseDTO segundo = service.create("Cliente B");

		verify(clientRepository, times(2)).save(captor.capture());
		verifyNoMoreInteractions(clientRepository);
		assertThat(primeiro.apiKey()).isNotEqualTo(segundo.apiKey());
		assertThat(primeiro.apiKey()).hasSizeGreaterThanOrEqualTo(43);
		assertThat(segundo.apiKey()).hasSizeGreaterThanOrEqualTo(43);
		var gravados = captor.getAllValues();
		assertThat(gravados.get(0).getApiKeyHash()).isEqualTo(ApiKeyHasher.hash(primeiro.apiKey()));
		assertThat(gravados.get(1).getApiKeyHash()).isEqualTo(ApiKeyHasher.hash(segundo.apiKey()));
		assertThat(gravados).allSatisfy(gravado -> assertThat(gravado.getCreatedAt()).isNotNull());
		assertThat(gravados.get(0)).hasNoNullFieldsOrPropertiesExcept("id")
				.extracting(ClientEntity::getName, ClientEntity::getApiKeyHash)
				.doesNotContain(primeiro.apiKey());
		assertThat(gravados.get(1))
				.extracting(ClientEntity::getName, ClientEntity::getApiKeyHash)
				.doesNotContain(segundo.apiKey());
	}

	@Test
	@DisplayName("CT-024 — Cliente inexistente gera ResourceNotFoundException")
	void deveLancarResourceNotFoundParaClienteInexistente() {
		when(clientRepository.findById(999999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(999999L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("999999");
	}

}
