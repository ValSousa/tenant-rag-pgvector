package br.com.rag_pgvector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.rag_pgvector.dto.AnswerResponseDTO;
import br.com.rag_pgvector.dto.SourceResponseDTO;
import br.com.rag_pgvector.enums.DocumentTypeEnum;
import br.com.rag_pgvector.service.AnswerService;
import br.com.rag_pgvector.service.ClientService;
import br.com.rag_pgvector.service.DocumentService;
import br.com.rag_pgvector.service.EmbeddingService;
import br.com.rag_pgvector.support.PostgresTestcontainersConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Qualidade com a OpenAI real (docs/QA/03, seção 9). Fora do {@code mvnw test}: gasta tokens e precisa de
 * {@code OPENAI_API_KEY}. Rodar com {@code ./mvnw test -Dgroups=openai -Dtest.excluded.groups=nenhum}.
 * Usa os beans reais (sem {@code AiTestConfig}) e o banco do Testcontainers; sem o perfil {@code test}, para a chave
 * da OpenAI vir da variável de ambiente. CT-035 (RF-004), CT-075 e CT-076 (RF-008). Os 6 PDFs de
 * {@code documents/} são ingeridos uma vez, na primeira pergunta, pelos services reais.
 */
@Tag("openai")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@SpringBootTest(properties = {
		"app.security.admin-api-key=nao-usada-no-opt-in",
		// O Testcontainers (@ServiceConnection) substitui a conexão; estes valores só evitam placeholder sem valor
		"spring.datasource.username=test",
		"spring.datasource.password=test"})
@Import(PostgresTestcontainersConfig.class)
class RagQualityOpenAiIT {

	private static final Path DOCUMENTS = Path.of("documents");
	private static final Map<String, DocumentTypeEnum> ARQUIVOS = Map.of(
			"contrato.pdf", DocumentTypeEnum.CONTRACT,
			"sinistro.pdf", DocumentTypeEnum.CLAIM,
			"vistoria.pdf", DocumentTypeEnum.INSPECTION);

	/** Cliente do gabarito ("A" ou "B") → id gravado; preenchido na primeira pergunta (contexto compartilhado). */
	private static final Map<String, Long> CLIENTES = new HashMap<>();

	@Autowired
	EmbeddingService embeddingService;

	@Autowired
	ClientService clientService;

	@Autowired
	DocumentService documentService;

	@Autowired
	AnswerService answerService;

	@Test
	@DisplayName("CT-035 — Textos parecidos ficam mais próximos (OpenAI real)")
	void deveAproximarTextosParecidos() {
		Embedding franquiaApolice = embeddingService.embedQuery("franquia da apólice");
		Embedding valorFranquia = embeddingService.embedQuery("valor da franquia");
		Embedding dataSinistro = embeddingService.embedQuery("data do sinistro");

		assertThat(CosineSimilarity.between(franquiaApolice, valorFranquia))
				.isGreaterThan(CosineSimilarity.between(franquiaApolice, dataSinistro));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@CsvFileSource(resources = "/ddt/gabarito-perguntas.csv", delimiter = ';', numLinesToSkip = 1)
	@DisplayName("CT-075 — Perguntas da seção 10 do README (OpenAI real, DDT)")
	void deveResponderAsPerguntasDoReadme(String cenario, String cliente, String pergunta, String termosEsperados,
			String tipoFonteEsperado, String termosProibidos) throws IOException {
		AnswerResponseDTO resposta = answerService.answer(clienteDoGabarito(cliente), pergunta, null);

		for (String termo : termosEsperados.split("\\|")) {
			assertThat(resposta.answer()).as("%s: termo esperado", cenario).containsIgnoringCase(termo);
		}
		for (String termo : termosProibidos.split("\\|")) {
			assertThat(resposta.answer()).as("%s: valor do outro cliente", cenario).doesNotContain(termo);
		}
		List<DocumentTypeEnum> tiposCitados = resposta.sources().stream().map(SourceResponseDTO::documentType).toList();
		assertThat(tiposCitados).as("%s: tipos de documento nas fontes", cenario).containsAll(
				Arrays.stream(tipoFonteEsperado.split("\\|")).map(DocumentTypeEnum::valueOf).toList());
	}

	@Test
	@DisplayName("CT-076 — Pergunta sem resposta nos documentos (OpenAI real)")
	void deveInformarQueAInformacaoNaoFoiEncontrada() throws IOException {
		AnswerResponseDTO resposta = answerService.answer(clienteDoGabarito("A"), "Qual a cor do carro do vizinho?",
				null);

		assertThat(resposta.answer())
				.containsPattern("(?i)não (foi )?encontrad")
				.doesNotContainPattern("R\\$\\s*\\d");
	}

	private Long clienteDoGabarito(String cliente) throws IOException {
		synchronized (CLIENTES) {
			if (CLIENTES.isEmpty()) {
				CLIENTES.put("A", ingerir("Cliente A", "cliente-a"));
				CLIENTES.put("B", ingerir("Cliente B", "cliente-b"));
			}
			return CLIENTES.get(cliente);
		}
	}

	private long ingerir(String nome, String pasta) throws IOException {
		long clientId = clientService.create(nome).id();
		for (Map.Entry<String, DocumentTypeEnum> arquivo : ARQUIVOS.entrySet()) {
			byte[] pdf = Files.readAllBytes(DOCUMENTS.resolve(pasta).resolve(arquivo.getKey()));
			documentService.ingest(clientId, arquivo.getKey(), arquivo.getValue(), pdf);
		}
		return clientId;
	}

}
