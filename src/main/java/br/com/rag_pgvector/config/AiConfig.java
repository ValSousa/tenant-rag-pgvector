package br.com.rag_pgvector.config;

import java.util.List;

import javax.sql.DataSource;

import br.com.rag_pgvector.service.RagAssistant;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

/**
 * Beans do LangChain4j montados à mão, sem starters (ADR-005): modelo de embeddings, store vetorial e, para a resposta
 * do {@code /ask}, o modelo de chat e o {@link RagAssistant}.
 */
@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, RagProperties.class})
public class AiConfig {

	/** Tabela do store, criada pela migration V1 (ADR-004). */
	static final String CHUNK_TABLE = "document_chunk";

	/**
	 * Colunas de metadados do modo {@code COLUMN_PER_KEY}: a chave do metadado é o nome da coluna. Precisam ser iguais
	 * às da migration V1 (arquitetura 04, seção 3).
	 */
	static final List<String> CHUNK_METADATA_COLUMNS = List.of(
			"client_id BIGINT NOT NULL",
			"document_id BIGINT NOT NULL",
			"chunk_index INTEGER NOT NULL",
			"document_type VARCHAR(100) NOT NULL",
			"file_name VARCHAR(255) NOT NULL");

	@Bean
	EmbeddingModel embeddingModel(OpenAiProperties openAi, RagProperties rag) {
		return OpenAiEmbeddingModel.builder()
				.apiKey(openAi.apiKey())
				.modelName(openAi.embeddingModel())
				.dimensions(rag.embeddingDimension())
				.build();
	}

	/** Modelo que gera a resposta (ADR-006): {@code gpt-4o-mini} com temperatura baixa, ambos configuráveis. */
	@Bean
	ChatModel chatModel(OpenAiProperties openAi) {
		return OpenAiChatModel.builder()
				.apiKey(openAi.apiKey())
				.modelName(openAi.chatModel())
				.temperature(openAi.temperature())
				.build();
	}

	/**
	 * Assistente do RAG sem {@code ContentRetriever}: recebe só os trechos que o {@code AnswerService} buscou com o
	 * filtro do cliente (ADR-011, arquitetura 07).
	 */
	@Bean
	RagAssistant ragAssistant(ChatModel chatModel) {
		return AiServices.create(RagAssistant.class, chatModel);
	}

	/**
	 * Store vetorial sobre a tabela {@code document_chunk}. Usado só pelo {@code ChunkRepository}, que sempre
	 * filtra por {@code client_id}. O {@link TransactionAwareDataSourceProxy} faz o INSERT dos chunks entrar na
	 * transação do JPA (arquitetura 02, seção 3.3). Tabela e extensão vêm do Flyway: o store não executa DDL.
	 */
	@Bean
	EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource, RagProperties rag) {
		return PgVectorEmbeddingStore.datasourceBuilder()
				.datasource(new TransactionAwareDataSourceProxy(dataSource))
				.table(CHUNK_TABLE)
				.dimension(rag.embeddingDimension())
				.createTable(false)
				.skipCreateVectorExtension(true)
				.metadataStorageConfig(DefaultMetadataStorageConfig.builder()
						.storageMode(MetadataStorageMode.COLUMN_PER_KEY)
						.columnDefinitions(CHUNK_METADATA_COLUMNS)
						.build())
				.build();
	}

}
