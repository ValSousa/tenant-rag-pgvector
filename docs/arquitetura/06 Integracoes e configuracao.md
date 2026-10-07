# 06 — Integrações e configuração

## 1. Mapa de integrações

| Integração | Protocolo | Biblioteca | Usada por | Falha vira |
|---|---|---|---|---|
| PostgreSQL — dados relacionais | JDBC | Driver PostgreSQL, Spring Data JPA, Flyway | `ClientRepository`, `DocumentRepository`, migrations | 500 (ou falha na inicialização) |
| PostgreSQL — vetores | JDBC | LangChain4j `PgVectorEmbeddingStore` | `ChunkRepository` | 500 |
| OpenAI — embeddings | HTTPS | LangChain4j `OpenAiEmbeddingModel` | `EmbeddingService` | `AiProviderException` → 503 |
| OpenAI — chat | HTTPS | LangChain4j `OpenAiChatModel` + `AiServices` | `AnswerService` (via `RagAssistant`) | `AiProviderException` → 503 |
| Arquivos PDF | em memória | LangChain4j `ApachePdfBoxDocumentParser` | `PdfTextExtractor` | `InvalidDocumentException` → 422 |

## 2. PostgreSQL + pgvector

- Imagem `pgvector/pgvector:pg17` (ADR-002).
- `docker/init.sql` cria a extensão na primeira subida do volume; a migration V1 também, por segurança.
- As tabelas, inclusive `document_chunk` (a tabela do `PgVectorEmbeddingStore`), são criadas pelo Flyway (ADR-003, ADR-004).

## 3. LangChain4j + OpenAI (ADR-005, ADR-006)

### 3.1 Modelos

| Uso | Modelo | Configuração |
|---|---|---|
| Embeddings | `text-embedding-3-small` | `dimensions = 768` |
| Chat (RF-008) | `gpt-4o-mini` (configurável) | `temperature = 0.1` |

Chave: variável de ambiente `OPENAI_API_KEY`.

### 3.2 `AiConfig`

Beans montados à mão (sem starters Spring Boot do LangChain4j):

```java
@Configuration
public class AiConfig {

    @Bean
    EmbeddingModel embeddingModel(OpenAiProperties ai, RagProperties rag) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(ai.apiKey())
                .modelName(ai.embeddingModel())          // text-embedding-3-small
                .dimensions(rag.embeddingDimension())    // 768
                .build();
    }

    @Bean
    ChatModel chatModel(OpenAiProperties ai) {
        return OpenAiChatModel.builder()
                .apiKey(ai.apiKey())
                .modelName(ai.chatModel())               // gpt-4o-mini
                .temperature(ai.temperature())           // 0.1
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource, RagProperties rag) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(new TransactionAwareDataSourceProxy(dataSource))
                .table("document_chunk")
                .dimension(rag.embeddingDimension())
                .createTable(false)                      // tabela criada pelo Flyway
                .skipCreateVectorExtension(true)         // extensão criada pelo Flyway
                .metadataStorageConfig(DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                        .columnDefinitions(List.of(
                                "client_id BIGINT NOT NULL",
                                "document_id BIGINT NOT NULL",
                                "chunk_index INTEGER NOT NULL",
                                "document_type VARCHAR(100) NOT NULL",
                                "file_name VARCHAR(255) NOT NULL"))
                        .build())
                .build();
    }

    @Bean
    RagAssistant ragAssistant(ChatModel chatModel) {
        return AiServices.create(RagAssistant.class, chatModel);
    }
}
```

- `OpenAiProperties` é um `@ConfigurationProperties("app.openai")` (pacote `config`), ao lado do `RagProperties`.
- As `columnDefinitions` precisam ter os mesmos nomes da migration V1 ([04](04%20Modelo%20de%20dados.md), seção 3). No modo `COLUMN_PER_KEY`, **a chave do metadado é o nome da coluna**: `client_id`, não `clientId`.
- Nomes de builders e métodos conferidos no código do `langchain4j-pgvector` (linha 1.20.x, módulo beta). Conferir de novo ao atualizar a versão.

### 3.3 `EmbeddingService`

```java
@Service
public class EmbeddingService {
    public Embedding embedQuery(String question) { ... }
    public List<Embedding> embedDocuments(List<TextSegment> segments) { ... }
}
```

1. Rejeita texto vazio (`IllegalArgumentException`).
2. Chama `embeddingModel.embed(...)` / `embeddingModel.embedAll(...)`; uma chamada para todos os chunks do documento.
3. Confere `embedding.dimension() == app.rag.embedding-dimension`; se diferente, `AiProviderException` ("dimensão do modelo não confere com o banco").
4. Converte exceções da OpenAI (timeout, 401, 429, 5xx) em `AiProviderException`.

### 3.4 `RagAssistant` (AiServices)

```java
public interface RagAssistant {

    @SystemMessage("""
        Você é um assistente que responde perguntas sobre documentos de seguro de um único cliente.
        Use somente as informações dos trechos numerados fornecidos.
        Se a resposta não estiver nos trechos, diga que a informação não foi encontrada nos documentos do cliente.
        Não invente valores, datas ou percentuais.
        Quando fizer cálculos, mostre os números usados.
        Cite os trechos usados no formato [n].
        Responda em português do Brasil.
        """)
    @UserMessage("""
        Trechos:
        {{context}}

        Pergunta: {{question}}
        """)
    String answer(@V("context") String context, @V("question") String question);
}
```

O `AnswerService` monta `context` com os chunks já filtrados por cliente, um por linha: `[1] (contrato.pdf, CONTRACT) <texto>`. O `RagAssistant` **não** tem `ContentRetriever`; ele nunca busca no banco sozinho (ADR-011).

## 4. PDF e chunking (ADR-007)

- `ApachePdfBoxDocumentParser().parse(inputStream)` → `Document`; texto vazio → `InvalidDocumentException`.
- `DocumentSplitters.recursive(1000, 200).split(document)` → `List<TextSegment>`; o `TextChunker` numera e devolve `Chunk(index, content)`.
- O arquivo não é guardado em disco nem no banco; só o texto em chunks.

## 5. `docker-compose.yml`

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
    container_name: rag-postgres
    environment:
      POSTGRES_DB: ragdb
      POSTGRES_USER: rag
      POSTGRES_PASSWORD: rag
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./docker/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U rag -d ragdb"]
      interval: 5s
      retries: 10

volumes:
  pgdata:
```

`docker/init.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Credenciais `rag/rag` são só de desenvolvimento local. Os modelos rodam na OpenAI; não há contêiner de IA.

## 6. `application.properties`

```properties
spring.application.name=tenant-rag-pgvector

# Banco
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/ragdb}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true

# Erros (ADR-010)
spring.mvc.problemdetails.enabled=true

# Documentação da API (ADR-013)
springdoc.api-docs.enabled=${SWAGGER_ENABLED:true}
springdoc.swagger-ui.enabled=${SWAGGER_ENABLED:true}
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.persist-authorization=true

# Upload
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB
# Limite reduzido de 10 MB para 5 MB por decisão do usuário em 2026-10-05 (RF-006 DP-02).
# O Tomcat descarta o corpo recusado até este tamanho antes de responder; precisa ficar acima do
# max-request-size para o cliente receber o 413 (achado #1 da revisão do RF-006).
server.tomcat.max-swallow-size=10MB

# OpenAI (ADR-006)
app.openai.api-key=${OPENAI_API_KEY}
app.openai.embedding-model=text-embedding-3-small
app.openai.chat-model=gpt-4o-mini
app.openai.temperature=0.1

# RAG
app.rag.embedding-dimension=768
app.rag.chunk-size=1000
app.rag.chunk-overlap=200
app.rag.default-top-k=5
app.rag.answer-top-k=8
app.rag.max-top-k=20

# Segurança
app.security.admin-api-key=${ADMIN_API_KEY}

# Log por requisição (ADR-015 — entra quando o RF-013 for implementado; seção 9)
app.logging.environment=${APP_ENVIRONMENT:local}
logging.pattern.correlation=[%X{traceId:-}] 
```

- O `application.properties` é versionado como exemplo e não guarda senha nem chave. `DB_USER`, `DB_PASSWORD`, `OPENAI_API_KEY` e `ADMIN_API_KEY` **não** têm valor padrão; para desenvolvimento local, o usuário e a senha do banco ficam no `application-dev.properties`, que está no `.gitignore` (perfil `dev`).
- `OPENAI_API_KEY` e `ADMIN_API_KEY` **não** têm valor padrão: sem elas a aplicação não sobe. Defina na sessão do terminal ou na configuração de execução da IDE. Nunca grave a chave em arquivo versionado.
- Perfil `test` (`src/test/resources/application-test.properties`): `app.openai.api-key=test-key-nao-usada` e `app.security.admin-api-key=test-admin-key`. Os beans `EmbeddingModel` e `ChatModel` são substituídos nos testes (ADR-012), então a chave falsa nunca chega à OpenAI.

## 7. OpenAPI e Swagger UI (ADR-013)

Dependência: `org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.1.x (linha compatível com Spring Boot 4.1; versão não gerenciada pelo Boot, declarar no `pom.xml`).

`config/OpenApiConfig`:

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(title = "tenant-rag-pgvector", version = "v1",
                 description = "RAG multi-tenant com PostgreSQL + pgvector"),
    security = @SecurityRequirement(name = "apiKey"))
@SecurityScheme(
    name = "apiKey",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "X-API-Key")
public class OpenApiConfig {
}
```

Anotações do pacote `io.swagger.v3.oas.annotations`. `persist-authorization=true` mantém a chave informada no **Authorize** ao recarregar a página.

## 8. Comandos do dia a dia

```bash
docker compose up -d                       # banco
export OPENAI_API_KEY=sk-...               # PowerShell: $env:OPENAI_API_KEY="sk-..."
export ADMIN_API_KEY=minha-chave-local     # PowerShell: $env:ADMIN_API_KEY="minha-chave-local"
./mvnw spring-boot:run                     # Windows: mvnw.cmd spring-boot:run
# Swagger UI: http://localhost:8080/swagger-ui.html
./mvnw test                                # precisa de Docker (Testcontainers), não precisa de OPENAI_API_KEY
```

## 9. Log por requisição (ADR-015)

Para o RF-013 (Etapa 8 do plano); os valores abaixo seguem as decisões DP-02 a DP-07 do RF-013, aprovadas em 2026-10-06 com as recomendações da ADR-015. Nenhuma dependência nova: SLF4J, Logback e MDC vêm com o `spring-boot-starter-webmvc`.

| Propriedade | Valor | Para quê |
|---|---|---|
| `app.logging.environment` | `${APP_ENVIRONMENT:local}` | Campo `environment` da linha de requisição (DP-04). Lida pelo `RequestLoggingFilter` com `@Value`. |
| `spring.application.name` (já existe) | `tenant-rag-pgvector` | Campo `service` (DP-04). |
| `logging.pattern.correlation` | `[%X{traceId:-}] ` (com o espaço final) | Põe o trace ID do MDC no padrão de console do Spring Boot (`${LOG_CORRELATION_PATTERN}` do `defaults.xml`), em **todas** as linhas da requisição, inclusive o `log.error` do `GlobalExceptionHandler` (DP-03). Fora de uma requisição o valor sai vazio (`[]`). |
| `logging.structured.format.console` | não definida (texto, DP-02) | Só se um dia o formato passar a JSON: `ecs` ou `logstash`. O MDC (`traceId`) vira campo do JSON e a mensagem chave=valor continua no campo `message`. |

Conferido no `spring-boot-4.1.1.jar` (repositório Maven local): `logging.pattern.correlation` e `logging.structured.format.console` existem nos metadados de configuração, e o `defaults.xml` do Logback usa `${LOG_CORRELATION_PATTERN:-}` no padrão de console e de arquivo. A ordem `-100` da cadeia do Spring Security (`spring.security.filter.order`) foi conferida no `spring-boot-security-4.1.1.jar`.

Configurações que **não** podem entrar em arquivo versionado, porque registrariam segredos ou dados pessoais (RF-013 RN-05/RN-06):

- `logging.level.org.springframework.web`, `org.springframework.security` ou de clientes HTTP em `DEBUG`/`TRACE` (cabeçalhos, incluindo `X-API-Key`);
- `logging.level.org.hibernate.orm.jdbc.bind=TRACE` e `spring.jpa.show-sql=true` com parâmetros (o `api_key_hash` aparece como parâmetro da consulta da chave);
- `logRequests(true)`/`logResponses(true)` nos builders do `AiConfig` (cabeçalho com a chave da OpenAI e o conteúdo enviado ao modelo);
- `CommonsRequestLoggingFilter` com cabeçalhos, query string ou corpo.
