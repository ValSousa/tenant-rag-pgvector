package br.com.rag_pgvector.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A configuração não liga log de cabeçalhos, de SQL com parâmetros nem do conteúdo enviado à OpenAI (ADR-015,
 * decisão 7): níveis DEBUG/TRACE desses loggers registrariam a {@code X-API-Key}, o {@code api_key_hash} ou a
 * pergunta. Lê os arquivos de propriedades e o código-fonte a partir da pasta do projeto (onde o Maven roda).
 */
class LoggingConfigurationTest {

	/** Loggers que, em DEBUG/TRACE, registram cabeçalhos, parâmetros de SQL ou o tráfego HTTP com a OpenAI. */
	private static final List<String> LOGGERS_SENSIVEIS = List.of("org.springframework.web",
			"org.springframework.security", "org.hibernate.orm.jdbc.bind", "org.hibernate.SQL", "dev.langchain4j",
			"org.apache.http", "org.apache.hc", "okhttp3", "java.net.http", "jdk.httpclient", "reactor.netty");

	@Test
	@DisplayName("CT-148 — Configuração versionada não liga log de cabeçalhos, SQL com parâmetros ou da OpenAI")
	void naoDeveLigarLogDeCabecalhosSqlOuOpenAi() throws IOException {
		List<Path> arquivos = arquivosDePropriedades();
		assertThat(arquivos).isNotEmpty();

		for (Path arquivo : arquivos) {
			Properties propriedades = carregar(arquivo);
			for (String chave : propriedades.stringPropertyNames()) {
				String valor = propriedades.getProperty(chave).trim().toUpperCase(Locale.ROOT);
				if (chave.startsWith("logging.level.") && afetaLoggerSensivel(chave.substring("logging.level.".length()))) {
					assertThat(valor).as("%s em %s", chave, arquivo).isNotIn("DEBUG", "TRACE", "ALL");
				}
				String normalizada = chave.toLowerCase(Locale.ROOT).replace("-", "");
				if (normalizada.contains("logrequests") || normalizada.contains("logresponses")) {
					assertThat(valor).as("%s em %s", chave, arquivo).isNotEqualTo("TRUE");
				}
			}
			assertThat(propriedades.getProperty("spring.jpa.show-sql", "false").trim())
					.as("spring.jpa.show-sql em %s", arquivo).isNotEqualToIgnoringCase("true");
		}
	}

	@Test
	@DisplayName("CT-148 — Código não liga logRequests/logResponses do LangChain4j nem o CommonsRequestLoggingFilter")
	void naoDeveLigarLogDeRequisicoesNoCodigo() throws IOException {
		try (Stream<Path> fontes = Files.walk(Path.of("src/main/java"))) {
			for (Path fonte : fontes.filter(caminho -> caminho.toString().endsWith(".java")).toList()) {
				String codigo = Files.readString(fonte, StandardCharsets.UTF_8).replace(" ", "");
				assertThat(codigo).as(fonte.toString())
						.doesNotContain("logRequests(true)", "logResponses(true)", "CommonsRequestLoggingFilter");
			}
		}
	}

	private static boolean afetaLoggerSensivel(String logger) {
		if ("root".equals(logger)) {
			return true;
		}
		return LOGGERS_SENSIVEIS.stream().anyMatch(sensivel -> sensivel.equals(logger)
				|| sensivel.startsWith(logger + ".") || logger.startsWith(sensivel + "."));
	}

	private static List<Path> arquivosDePropriedades() throws IOException {
		List<Path> arquivos = new ArrayList<>();
		try (Stream<Path> principais = Files.list(Path.of("src/main/resources"))) {
			principais.filter(caminho -> caminho.getFileName().toString().matches("application.*\\.properties"))
					.forEach(arquivos::add);
		}
		arquivos.add(Path.of("src/test/resources/application-test.properties"));
		return arquivos;
	}

	private static Properties carregar(Path arquivo) throws IOException {
		Properties propriedades = new Properties();
		try (Reader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
			propriedades.load(leitor);
		}
		return propriedades;
	}

}
