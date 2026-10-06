package br.com.rag_pgvector;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_B;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import br.com.rag_pgvector.logging.RequestLoggingFilter;
import br.com.rag_pgvector.security.ApiKeyAuthenticationFilter;
import br.com.rag_pgvector.support.AiTestConfig;
import br.com.rag_pgvector.support.PostgresTestcontainersConfig;
import br.com.rag_pgvector.support.TestDataBuilder;
import br.com.rag_pgvector.support.TestPdfFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Limite de upload com servidor real (docs/QA/03, seção 6): o MockMvc não aplica o
 * {@code spring.servlet.multipart.max-file-size}, quem aplica é o Tomcat. Usa um contexto próprio (porta aleatória),
 * fechado ao fim da classe para liberar memória e o contêiner. Usa o {@code server.tomcat.max-swallow-size} do
 * {@code application.properties}, para testar a configuração real (achado #1 da revisão do RF-006).
 * Aproveita o servidor real para conferir o cabeçalho {@code X-Trace-Id} do log por requisição nos 401, 403 e 413.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgresTestcontainersConfig.class, AiTestConfig.class, TestDataBuilder.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UploadLimitIT {

	private static final int CINCO_MB = 5 * 1024 * 1024;
	private static final String TRACE_ID = "[0-9a-f]{32}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TestDataBuilder data;

	@BeforeEach
	void clienteA() {
		jdbc.execute("TRUNCATE document_chunk, document, client RESTART IDENTITY CASCADE");
		data.cliente("Cliente A", CHAVE_A);
	}

	@Test
	@DisplayName("CT-053 — Arquivo acima de 5 MB")
	void deveRecusarArquivoAcimaDe5Mb() {
		Resposta acimaDoLimite = enviar(TestPdfFactory.arquivoDeTamanho(CINCO_MB + 1));

		assertThat(acimaDoLimite.status()).isEqualTo(413);
		assertThat(acimaDoLimite.corpo()).contains("\"title\":\"Arquivo muito grande\"");
		assertThat(acimaDoLimite.corpo())
				.contains("\"detail\":\"O arquivo excede o tamanho máximo permitido de 5 MB.\"");
		assertThat(acimaDoLimite.contentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(acimaDoLimite.traceId()).as("X-Trace-Id no 413").matches(TRACE_ID);

		Resposta comoNoCt137 = enviar(TestPdfFactory.arquivoDeTamanho(6_000_000));

		assertThat(comoNoCt137.status()).isEqualTo(413);
		assertThat(comoNoCt137.corpo()).contains("\"title\":\"Arquivo muito grande\"");

		Resposta abaixoDoLimite = enviar(TestPdfFactory.arquivoDeTamanho(4 * 1024 * 1024));

		assertThat(abaixoDoLimite.status()).isNotEqualTo(413);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM document", Long.class)).isZero();
	}

	@Test
	@DisplayName("CT-145 (servidor real) — X-Trace-Id também nas respostas 401 e 403 da segurança")
	void deveDevolverOTraceIdNasRespostasDaSeguranca() {
		Resposta semChave = consultar(CLIENTE_A, null);
		Resposta outroCliente = consultar(CLIENTE_B, CHAVE_A);
		Resposta propria = consultar(CLIENTE_A, CHAVE_A);

		assertThat(semChave.status()).isEqualTo(401);
		assertThat(semChave.corpo()).contains("\"title\":\"Não autenticado\"");
		assertThat(semChave.traceId()).as("X-Trace-Id no 401").matches(TRACE_ID);
		assertThat(outroCliente.status()).isEqualTo(403);
		assertThat(outroCliente.corpo()).contains("\"title\":\"Acesso negado\"");
		assertThat(outroCliente.traceId()).as("X-Trace-Id no 403").matches(TRACE_ID);
		assertThat(propria.status()).isEqualTo(200);
		assertThat(propria.traceId()).matches(TRACE_ID).isNotEqualTo(semChave.traceId());
	}

	private Resposta consultar(long clientId, String chave) {
		return RestClient.create("http://localhost:" + port)
				.get()
				.uri("/clients/{clientId}", clientId)
				.headers(cabecalhos -> {
					if (chave != null) {
						cabecalhos.set(ApiKeyAuthenticationFilter.HEADER, chave);
					}
				})
				.exchange((request, response) -> resposta(response));
	}

	private Resposta enviar(byte[] arquivo) {
		MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
		partes.add("file", new ByteArrayResource(arquivo) {
			@Override
			public String getFilename() {
				return "grande.pdf";
			}
		});
		partes.add("documentType", "CONTRACT");
		return RestClient.create("http://localhost:" + port)
				.post()
				.uri("/clients/{clientId}/documents", CLIENTE_A)
				.header(ApiKeyAuthenticationFilter.HEADER, CHAVE_A)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(partes)
				.exchange((request, response) -> resposta(response));
	}

	private static Resposta resposta(ClientHttpResponse response) throws IOException {
		return new Resposta(response.getStatusCode().value(), String.valueOf(response.getHeaders().getContentType()),
				corpo(response.getBody().readAllBytes()),
				response.getHeaders().getFirst(RequestLoggingFilter.TRACE_ID_HEADER));
	}

	private static String corpo(byte[] bytes) throws IOException {
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private record Resposta(int status, String contentType, String corpo, String traceId) {
	}

}
