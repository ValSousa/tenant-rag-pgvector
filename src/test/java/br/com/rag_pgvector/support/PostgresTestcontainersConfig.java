package br.com.rag_pgvector.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL + pgvector temporário para os testes (ADR-012).
 * Usa o mesmo docker/init.sql do docker-compose.yml, para o banco de teste nascer igual ao local; o pom.xml copia
 * o arquivo para o classpath de teste, então não depende da pasta em que a JVM roda.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(
				DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
				.withCopyFileToContainer(MountableFile.forClasspathResource("docker/init.sql"),
						"/docker-entrypoint-initdb.d/init.sql");
	}

}
