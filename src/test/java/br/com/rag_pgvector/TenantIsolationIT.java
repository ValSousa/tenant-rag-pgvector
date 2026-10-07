package br.com.rag_pgvector;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CHAVE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.entity.DocumentEntity;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.AbstractIntegrationTest;
import br.com.rag_pgvector.support.DdtValores;
import br.com.rag_pgvector.support.FakeChatModel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.http.MediaType;

/**
 * Isolamento entre clientes na busca, de ponta a ponta (HTTP → SearchService → ChunkRepository → PostgreSQL +
 * pgvector reais), com vetores fixos do {@code FakeEmbeddingModel} (RF-012, docs/QA/03, seção 7). O dono de cada
 * resultado é conferido no banco ({@code document_chunk.client_id}), não só pelo texto. No {@code /ask} (CT-114), o
 * {@code RagAssistant} real roda sobre o {@code FakeChatModel}, que guarda o prompt exatamente como iria para o modelo.
 */
class TenantIsolationIT extends AbstractIntegrationTest {

	private static final String PERGUNTA = "Qual é o valor da franquia da apólice?";

	private ClientEntity clienteA;
	private ClientEntity clienteB;

	@BeforeEach
	void criarClientes() {
		clienteA = data.cliente("Cliente A", CHAVE_A);
		clienteB = data.cliente("Cliente B", CHAVE_B);
	}

	@Test
	@DisplayName("CT-110 — Chunk idêntico de outro cliente não aparece")
	void naoDeveDevolverChunkIdenticoDeOutroCliente() throws Exception {
		chunks(clienteA, "vidros", 3);
		DocumentEntity contratoB = data.documento(clienteB, "contrato-b.pdf", DocumentTypeEnum.CONTRACT);
		data.chunks(contratoB, List.of("Franquia do Cliente B: R$ 5.000,00."), List.of(fakeEmbeddings.fixo("franquia")));
		fakeEmbeddings.registrar(PERGUNTA, fakeEmbeddings.fixo("franquia"));

		Resultado resultado = buscar(clienteA, CHAVE_A, 20);

		assertThat(resultado.donos()).isNotEmpty().containsOnly(clienteA.getId());
		assertThat(resultado.textos()).doesNotContain("Franquia do Cliente B: R$ 5.000,00.");
	}

	@Test
	@DisplayName("CT-111 — Cliente vazio não vê dados de outro")
	void clienteSemDocumentosNaoDeveVerDadosDeOutro() throws Exception {
		chunks(clienteB, "franquia", 10);
		fakeEmbeddings.registrar(PERGUNTA, fakeEmbeddings.fixo("franquia"));

		Resultado resultado = buscar(clienteA, CHAVE_A, null);

		assertThat(resultado.textos()).isEmpty();
	}

	@Test
	@DisplayName("CT-112 — topK alto não \"completa\" com dados de outro cliente")
	void topKAltoNaoDeveCompletarComDadosDeOutroCliente() throws Exception {
		chunks(clienteA, "vidros", 3);
		chunks(clienteB, "franquia", 50);
		fakeEmbeddings.registrar(PERGUNTA, fakeEmbeddings.fixo("franquia"));

		Resultado resultado = buscar(clienteA, CHAVE_A, 20);

		assertThat(resultado.donos()).hasSize(3).containsOnly(clienteA.getId());
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@CsvFileSource(resources = "/ddt/isolation-matrix.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-113 — Matriz de isolamento A↔B (DDT)")
	void deveIsolarClientesConformeAMatriz(String cenario, String consultante, String vetorChunkA,
			String vetorChunkB, String vetorPergunta, String resultadosEsperados) throws Exception {
		String rotuloA = DdtValores.resolver(vetorChunkA);
		String rotuloB = DdtValores.resolver(vetorChunkB);
		if (rotuloA != null) {
			chunks(clienteA, rotuloA, 2);
		}
		if (rotuloB != null) {
			chunks(clienteB, rotuloB, 2);
		}
		fakeEmbeddings.registrar(PERGUNTA, fakeEmbeddings.fixo(vetorPergunta));

		Resultado resultado = "A".equals(consultante)
				? buscar(clienteA, CHAVE_A, 20)
				: buscar(clienteB, CHAVE_B, 20);

		if ("VAZIO".equals(resultadosEsperados)) {
			assertThat(resultado.textos()).isEmpty();
		}
		else {
			long esperado = "A".equals(resultadosEsperados) ? clienteA.getId() : clienteB.getId();
			assertThat(resultado.donos()).isNotEmpty().containsOnly(esperado);
		}
	}

	@Test
	@DisplayName("CT-114 — Contexto da resposta só tem dados do cliente")
	void contextoDaRespostaSoDeveTerDadosDoCliente() throws Exception {
		chunks(clienteA, "vidros", 10);
		DocumentEntity contratoB = data.documento(clienteB, "contrato-b.pdf", DocumentTypeEnum.CONTRACT);
		data.chunks(contratoB, List.of("Franquia do Cliente B: R$ 5.000,00."), List.of(fakeEmbeddings.fixo("franquia")));
		fakeEmbeddings.registrar(PERGUNTA, fakeEmbeddings.fixo("franquia"));

		String resposta = mvc.perform(post("/clients/{clientId}/ask", clienteA.getId())
						.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"%s\"}".formatted(PERGUNTA)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(fakeChat.mensagensDoUsuario()).hasSize(1);
		String prompt = fakeChat.mensagensDoUsuario().getFirst();
		List<String> trechos = prompt.lines().filter(linha -> linha.matches("^\\[\\d+] .*")).toList();
		assertThat(trechos).hasSize(8).allMatch(linha -> linha.contains("Cliente A: trecho"));
		assertThat(prompt)
				.contains("Pergunta: " + PERGUNTA)
				.doesNotContain("Cliente B")
				.doesNotContain("5.000,00")
				.doesNotContain("contrato-b.pdf");
		List<Integer> documentos = JsonPath.read(resposta, "$.sources[*].documentId");
		assertThat(documentos).hasSize(8).allMatch(id -> jdbc.queryForObject(
				"SELECT client_id FROM document WHERE id = ?", Long.class, id).equals(clienteA.getId()));
		assertThat(JsonPath.<String>read(resposta, "$.answer")).isEqualTo(FakeChatModel.RESPOSTA);
	}

	private void chunks(ClientEntity cliente, String rotulo, int quantidade) {
		DocumentEntity documento = data.documento(cliente, rotulo + "-" + cliente.getName() + ".pdf",
				DocumentTypeEnum.CONTRACT);
		List<String> textos = IntStream.range(0, quantidade)
				.mapToObj(i -> "%s: trecho %d sobre %s".formatted(cliente.getName(), i, rotulo))
				.toList();
		data.chunks(documento, textos, Collections.nCopies(quantidade, fakeEmbeddings.fixo(rotulo)));
	}

	private Resultado buscar(ClientEntity cliente, String chave, Integer topK) throws Exception {
		String corpo = topK == null
				? "{\"question\":\"%s\"}".formatted(PERGUNTA)
				: "{\"question\":\"%s\",\"topK\":%d}".formatted(PERGUNTA, topK);
		String resposta = mvc.perform(post("/clients/{clientId}/search", cliente.getId())
						.header(ApiKeyAuthenticationFilter.HEADER, chave)
						.contentType(MediaType.APPLICATION_JSON)
						.content(corpo))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> chunkIds = JsonPath.read(resposta, "$.results[*].chunkId");
		List<String> textos = JsonPath.read(resposta, "$.results[*].content");
		List<Long> donos = chunkIds.stream()
				.map(id -> jdbc.queryForObject("SELECT client_id FROM document_chunk WHERE embedding_id = ?::uuid",
						Long.class, id))
				.toList();
		return new Resultado(textos, donos);
	}

	private record Resultado(List<String> textos, List<Long> donos) {
	}

}
