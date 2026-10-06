package br.com.rag_pgvector.support;

import br.com.rag_pgvector.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes de integração (*IT): contexto completo + PostgreSQL real do Testcontainers + FakeEmbeddingModel e
 * FakeChatModel (o RagAssistant real roda sobre o modelo de chat falso, sem OpenAI).
 * Todas as subclasses usam a mesma configuração, então o contexto e o contêiner são reaproveitados.
 * O {@link ChunkRepository} é um spy (comportamento real), declarado aqui e não numa subclasse para não criar um
 * segundo contexto: o CT-056 o configura para falhar em {@code saveAll}; o Spring reseta o spy depois de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestcontainersConfig.class, AiTestConfig.class, TestDataBuilder.class})
public abstract class AbstractIntegrationTest {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected TestDataBuilder data;

	@Autowired
	protected FakeEmbeddingModel fakeEmbeddings;

	@Autowired
	protected FakeChatModel fakeChat;

	@MockitoSpyBean
	protected ChunkRepository chunkRepository;

	@BeforeEach
	void limparBanco() {
		jdbc.execute("TRUNCATE document_chunk, document, client RESTART IDENTITY CASCADE");
		fakeEmbeddings.reset();
		fakeChat.reset();
	}

}
