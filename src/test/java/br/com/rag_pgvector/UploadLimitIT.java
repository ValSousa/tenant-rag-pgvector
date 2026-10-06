package br.com.rag_pgvector;

import static br.com.rag_pgvector.support.Fixtures.CHAVE_A;
import static br.com.rag_pgvector.support.Fixtures.CLIENTE_A;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgresTestcontainersConfig.class, AiTestConfig.class, TestDataBuilder.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UploadLimitIT {

	private static final int CINCO_MB = 5 * 1024 * 1024;

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

		// mesmo tamanho do teste manual CT-137 (6.000.000 bytes)
		Resposta comoNoCt137 = enviar(TestPdfFactory.arquivoDeTamanho(6_000_000));

		assertThat(comoNoCt137.status()).isEqualTo(413);
		assertThat(comoNoCt137.corpo()).contains("\"title\":\"Arquivo muito grande\"");

		Resposta abaixoDoLimite = enviar(TestPdfFactory.arquivoDeTamanho(4 * 1024 * 1024));

		assertThat(abaixoDoLimite.status()).isNotEqualTo(413);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM document", Long.class)).isZero();
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
				.exchange((request, response) -> new Resposta(response.getStatusCode().value(),
						String.valueOf(response.getHeaders().getContentType()), corpo(response.getBody().readAllBytes())));
	}

	private static String corpo(byte[] bytes) throws IOException {
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private record Resposta(int status, String contentType, String corpo) {
	}

}
