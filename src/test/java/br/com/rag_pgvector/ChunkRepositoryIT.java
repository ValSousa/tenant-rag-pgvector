package br.com.rag_pgvector;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import br.com.rag_pgvector.entity.ClientEntity;
import br.com.rag_pgvector.entity.DocumentEntity;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * {@code ChunkRepository} com o {@code PgVectorEmbeddingStore} real (docs/QA/03, seção 5): gravação (CT-051) e busca
 * (CT-060, CT-066).
 */
class ChunkRepositoryIT extends AbstractIntegrationTest {

	@Test
	@DisplayName("CT-051 — Metadados do chunk gravados nas colunas")
	void deveGravarOsMetadadosNasColunasDeDocumentChunk() {
		ClientEntity cliente = data.cliente("Cliente A");
		DocumentEntity documento = data.documento(cliente, "vistoria.pdf", DocumentTypeEnum.INSPECTION);
		List<String> textos = List.of("Porta dianteira esquerda.", "Longarina dianteira.", "Pintura da lateral.");

		data.chunks(documento, textos,
				List.of(fakeEmbeddings.fixo("porta"), fakeEmbeddings.fixo("longarina"), fakeEmbeddings.fixo("pintura")));

		List<Map<String, Object>> linhas = jdbc.queryForList("""
				SELECT embedding_id, text, client_id, document_id, chunk_index, document_type, file_name,
				       vector_dims(embedding) AS dimensao
				FROM document_chunk ORDER BY chunk_index""");
		assertThat(linhas).hasSize(3);
		for (int i = 0; i < linhas.size(); i++) {
			Map<String, Object> linha = linhas.get(i);
			assertThat(linha.get("embedding_id")).isNotNull();
			assertThat(linha.get("text")).isEqualTo(textos.get(i));
			assertThat(linha.get("client_id")).isEqualTo(cliente.getId());
			assertThat(linha.get("document_id")).isEqualTo(documento.getId());
			assertThat(linha.get("chunk_index")).isEqualTo(i);
			assertThat(linha.get("document_type")).isEqualTo("INSPECTION");
			assertThat(linha.get("file_name")).isEqualTo("vistoria.pdf");
			assertThat(linha.get("dimensao")).isEqualTo(768);
		}
	}

	@Test
	@DisplayName("CT-060 — Busca devolve chunks do cliente ordenados por relevância")
	void deveDevolverOsChunksDoClienteOrdenadosPorRelevancia() throws Exception {
		ClientEntity cliente = data.cliente("Cliente A", CHAVE_A);
		DocumentEntity contrato = data.documento(cliente, "contrato.pdf", DocumentTypeEnum.CONTRACT);
		data.chunks(contrato, List.of("Franquia de R$ 3.500,00.", "Cobertura de vidros.", "Assistência 24 horas."),
				List.of(fakeEmbeddings.fixo("franquia"), fakeEmbeddings.fixo("vidros"),
						fakeEmbeddings.fixo("assistencia")));
		String pergunta = "Qual é o valor da franquia da apólice?";
		fakeEmbeddings.registrar(pergunta, combinar(Map.of("franquia", 0.9f, "vidros", 0.4f, "assistencia", 0.1f)));

		String resposta = mvc.perform(post("/clients/{clientId}/search", cliente.getId())
						.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"" + pergunta + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value(cliente.getId()))
				.andExpect(jsonPath("$.results[0].content").value("Franquia de R$ 3.500,00."))
				.andExpect(jsonPath("$.results[0].documentId").value(contrato.getId()))
				.andExpect(jsonPath("$.results[0].fileName").value("contrato.pdf"))
				.andExpect(jsonPath("$.results[0].documentType").value("CONTRACT"))
				.andExpect(jsonPath("$.results[0].chunkIndex").value(0))
				.andReturn().getResponse().getContentAsString();

		List<String> textos = JsonPath.read(resposta, "$.results[*].content");
		List<Double> scores = JsonPath.read(resposta, "$.results[*].score");
		assertThat(textos).hasSizeLessThanOrEqualTo(5)
				.containsExactly("Franquia de R$ 3.500,00.", "Cobertura de vidros.", "Assistência 24 horas.");
		assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder());
	}

	@Test
	@DisplayName("CT-066 — Faixa e significado do score")
	void deveDevolverScoreEntreZeroEUmComIdenticoProximoDeUm() {
		ClientEntity cliente = data.cliente("Cliente A");
		DocumentEntity documento = data.documento(cliente, "contrato.pdf", DocumentTypeEnum.CONTRACT);
		data.chunks(documento, List.of("idêntico", "ortogonal 1", "ortogonal 2"),
				List.of(fakeEmbeddings.fixo("franquia"), fakeEmbeddings.fixo("vidros"), fakeEmbeddings.fixo("pintura")));

		List<EmbeddingMatch<TextSegment>> encontrados = chunkRepository.searchByClient(cliente.getId(),
				fakeEmbeddings.fixo("franquia"), 5);

		assertThat(encontrados).hasSize(3);
		assertThat(encontrados.getFirst().embedded().text()).isEqualTo("idêntico");
		assertThat(encontrados.getFirst().score()).isGreaterThanOrEqualTo(0.999);
		assertThat(encontrados).allSatisfy(match -> assertThat(match.score()).isBetween(0.0, 1.0));
	}

	private Embedding combinar(Map<String, Float> pesos) {
		float[] vetor = new float[fakeEmbeddings.dimension()];
		pesos.forEach((rotulo, peso) -> {
			float[] eixo = fakeEmbeddings.fixo(rotulo).vector();
			for (int i = 0; i < vetor.length; i++) {
				vetor[i] += peso * eixo[i];
			}
		});
		return Embedding.from(vetor);
	}

}
