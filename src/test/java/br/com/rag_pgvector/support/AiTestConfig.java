package br.com.rag_pgvector.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Troca o {@code OpenAiEmbeddingModel} pelo {@link FakeEmbeddingModel} e o {@code OpenAiChatModel} pelo
 * {@link FakeChatModel}: o {@code mvnw test} nunca chama a OpenAI (ADR-012). O {@code RagAssistant} do
 * {@code AiConfig} é montado sobre o {@link FakeChatModel}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AiTestConfig {

	@Bean
	@Primary
	FakeEmbeddingModel fakeEmbeddingModel() {
		return new FakeEmbeddingModel();
	}

	@Bean
	@Primary
	FakeChatModel fakeChatModel() {
		return new FakeChatModel();
	}

}
