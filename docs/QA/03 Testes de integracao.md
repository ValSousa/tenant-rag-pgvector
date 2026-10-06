# 03 — Testes de integração

Testes com o contexto Spring completo, PostgreSQL + pgvector **real** (Testcontainers) e o `PgVectorEmbeddingStore` **real**. A OpenAI é substituída por um fake. Decisão técnica: ADR-012.

## 1. Por que integração aqui é obrigatória

A garantia de isolamento entre clientes está no SQL que o `PgVectorEmbeddingStore` gera (`WHERE client_id = ...`) e na FK composta do banco. Um mock de repository ou de `EmbeddingStore` não prova nenhuma das duas. Por isso os cenários P1 de isolamento (CT-110 a CT-114) e de rollback (CT-056) são de integração.

## 2. Infraestrutura

```text
src/test/java/br/com/rag_pgvector/
├── support/
│   ├── PostgresTestcontainersConfig.java
│   ├── AiTestConfig.java              (FakeEmbeddingModel @Primary)
│   ├── AbstractIntegrationTest.java
│   ├── FakeEmbeddingModel.java
│   ├── TestDataBuilder.java
│   ├── TestPdfFactory.java
│   └── Fixtures.java
src/test/resources/
├── application-test.properties
└── ddt/                               (massa CSV, ver 04)
```

### 2.1 Contêiner do banco

Implementado no RF-001:

```java
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(
                DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
                .withCopyFileToContainer(MountableFile.forHostPath("docker/init.sql"),
                        "/docker-entrypoint-initdb.d/init.sql");
    }
}
```

Import: `org.testcontainers.postgresql.PostgreSQLContainer` (Testcontainers 2, sem genérico). O `docker/init.sql` é o mesmo do `docker-compose.yml`.

### 2.2 Classe base

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestcontainersConfig.class, AiTestConfig.class})
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TestDataBuilder data;
    @Autowired protected FakeEmbeddingModel fakeEmbeddings;
    @MockitoBean protected RagAssistant ragAssistant;          // nunca chama a OpenAI

    @BeforeEach
    void limparBanco() {
        jdbc.execute("TRUNCATE document_chunk, document, client RESTART IDENTITY CASCADE");
        fakeEmbeddings.reset();
    }
}
```

**Estado atual (RF-001):** a classe base existe na forma mínima — `@SpringBootTest`, `@Import(PostgresTestcontainersConfig.class)` e `JdbcTemplate`. O `TRUNCATE`, o `TestDataBuilder`, o `FakeEmbeddingModel` e o `RagAssistant` mockado entram junto com os RFs que criam as tabelas e os beans (RF-002, RF-004, RF-008). O `@ActiveProfiles("test")` entra no RF-004, com o `application-test.properties`.

- Todas as classes `*IT` estendem esta base e usam **a mesma** combinação de `@Import` e `@MockitoBean`: o Spring reaproveita o contexto e o contêiner sobe uma vez só por execução.
- Limpeza por `TRUNCATE` em vez de `@Transactional` no teste: testes com servidor real (`UploadLimitIT`) rodam em outra thread e não veriam a transação do teste.

### 2.3 `application-test.properties`

```properties
app.openai.api-key=test-key-nao-usada
app.security.admin-api-key=test-admin-key
spring.jpa.open-in-view=false
logging.level.dev.langchain4j=INFO
```

## 3. Classes de apoio

### 3.1 `TestDataBuilder`

Cria dados pelo caminho real da aplicação (repositories JPA e `ChunkRepository`), sem SQL manual:

```java
var clienteA = data.cliente("Cliente A");                     // devolve ClientEntity + chave em texto
var contratoA = data.documento(clienteA, "contrato.pdf", DocumentTypeEnum.CONTRACT)
        .chunk("A franquia é de R$ 3.500,00.", fakeEmbeddings.fixo("franquia"))
        .chunk("Cobertura de vidros incluída.", fakeEmbeddings.fixo("vidros"))
        .gravar();
```

### 3.2 `FakeEmbeddingModel`

Implementa `dev.langchain4j.model.embedding.EmbeddingModel`:

| Método | Comportamento |
|---|---|
| `embedAll(List<TextSegment>)` | Para cada texto: se houver vetor registrado, devolve-o; senão gera um vetor de 768 posições a partir do hash SHA-256 do texto, normalizado (mesmo texto → mesmo vetor) |
| `dimension()` | 768 |
| `fixo(String rotulo)` | Vetor unitário num eixo próprio do rótulo (ex.: "franquia" → eixo 0). Rótulos diferentes são ortogonais: similaridade 0 entre si, 1 consigo mesmos |
| `registrar(String texto, Embedding vetor)` | Faz o texto devolver o vetor dado (usado para a pergunta) |
| `falharNaProxima(RuntimeException e)` | A próxima chamada lança a exceção (CT-055 em integração) |
| `chamadas()` | Quantas vezes foi chamado (para provar que a OpenAI não seria chamada) |
| `reset()` | Limpa registros e falhas |

Registrado como `@Primary` em `AiTestConfig`, no lugar do `OpenAiEmbeddingModel`.

### 3.3 `TestPdfFactory`

Gera PDFs em memória com PDFBox (disponível via `langchain4j-document-parser-apache-pdfbox`), para não versionar binários de teste:

| Método | Resultado |
|---|---|
| `comTexto(String... paragrafos)` | PDF de uma ou mais páginas com o texto |
| `comTamanho(int caracteres)` | PDF com texto repetido até o tamanho pedido |
| `semTexto()` | PDF válido com página em branco |
| `corrompido()` | Bytes que começam com `%PDF` mas não formam um PDF |
| `vazio()` | 0 bytes |

## 4. `SchemaMigrationIT` (RF-001, RF-002)

| Cenário | Verificação |
|---|---|
| CT-001 | `SELECT extname FROM pg_extension` contém `vector` |
| CT-002 | Contexto sobe com `ddl-auto=validate` (o próprio carregamento do teste) |
| CT-010 | Tabelas `client`, `document`, `document_chunk` e índice `document_chunk_embedding_idx` com método `hnsw` existem (`pg_indexes.indexdef`) |
| CT-011 | `format_type(atttypid, atttypmod)` de `document_chunk.embedding` = `vector(768)` |
| CT-012 | `INSERT` em `document` com `client_id` inexistente → `DataIntegrityViolationException` |
| CT-013 | Chunk com `client_id` diferente do documento → violação de `fk_chunk_document_client` |
| CT-014 | `document_type` fora da lista (DDT) → violação de `ck_document_type` |
| CT-015 | Apagar documento apaga seus chunks; apagar cliente com documento falha |

## 5. `ChunkRepositoryIT` (RF-006, RF-007)

| Cenário | Verificação |
|---|---|
| CT-051 | Depois de `saveAll`, as colunas `client_id`, `document_id`, `chunk_index`, `document_type`, `file_name` têm os valores certos (consulta direta com `JdbcTemplate`) |
| CT-060 | `searchByClient` devolve no máximo K, ordenados por `score` decrescente |
| CT-066 | Chunk com o mesmo vetor da pergunta tem `score` ≥ 0,999; todos os `score` entre 0 e 1 |

## 6. `DocumentIngestionIT` e `UploadLimitIT` (RF-005, RF-006)

| Cenário | Classe | Verificação |
|---|---|---|
| CT-050 | `DocumentIngestionIT` | `POST /clients/{A}/documents` com `TestPdfFactory.comTamanho(3500)` → 201; `totalChunks` = linhas em `document_chunk` com `document_id` do retorno; todas com `client_id` = A |
| CT-054 | `DocumentIngestionIT` | PDF corrompido e sem texto → 422; `document` e `document_chunk` vazios |
| CT-055 | `DocumentIngestionIT` | `fakeEmbeddings.falharNaProxima(...)` → 503; tabelas vazias |
| CT-056 | `DocumentIngestionIT` | `@MockitoSpyBean ChunkRepository` lança exceção em `saveAll` → 500; **`document` também vazio** (prova que o store entrou na transação via `TransactionAwareDataSourceProxy`) |
| CT-057 | `DocumentIngestionIT` | Chave de A em `/clients/{B}/documents` → 403; nada gravado para B |
| CT-058 | `DocumentIngestionIT` | Parte extra `clientId=B` no multipart enviada com a chave de A para `/clients/{A}/...` → documento gravado com `client_id` = A; `created_at` preenchido pelo sistema |
| CT-053 | `UploadLimitIT` | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestClient`: configuração real do `application.properties` (inclusive `server.tomcat.max-swallow-size`): arquivo de 5 MB + 1 byte e de 6.000.000 bytes (tamanho do CT-137) → 413 `ProblemDetail`; arquivo de 4 MB → não é 413 |

Se o CT-056 falhar com uma versão nova do `langchain4j-pgvector`, abrir BUG de severidade Alta e aplicar o plano B da arquitetura (compensação no `DocumentWriter`, [02 Componentes](../arquitetura/02%20Componentes%20e%20camadas.md), seção 3.3).

## 7. `TenantIsolationIT` (RF-012) — P1

Montagem padrão: Cliente A e Cliente B, cada um com documentos e chunks; vetores fixos do `FakeEmbeddingModel`.

| Cenário | Montagem | Verificação |
|---|---|---|
| CT-110 | B tem chunk com vetor **idêntico** ao da pergunta; A tem chunks ortogonais | Busca de A: nenhum resultado com `client_id` = B |
| CT-111 | A sem documentos; B com 10 chunks | Busca de A: lista vazia |
| CT-112 | A com 3 chunks, B com 50; `topK` = 20 | Busca de A: exatamente 3 resultados, todos de A |
| CT-113 | Matriz A↔B (DDT, [04](04%20Testes%20orientados%20a%20dados.md) seção 3.6) | Para cada linha, o consultante só vê os próprios chunks |
| CT-114 | Chunks em A e B; `POST /clients/{A}/ask` | O argumento `context` capturado do `RagAssistant` mockado só contém textos de A |

```java
class TenantIsolationIT extends AbstractIntegrationTest {

    @Autowired ChunkRepository chunkRepository;

    @Test
    @DisplayName("CT-110 — Busca do Cliente A não devolve chunk idêntico do Cliente B")
    void naoDeveRetornarChunkDeOutroClienteMesmoComEmbeddingIdentico() {
        var clienteA = data.cliente("Cliente A");
        var clienteB = data.cliente("Cliente B");
        var pergunta = fakeEmbeddings.fixo("franquia");

        data.documento(clienteA, "contrato.pdf", DocumentTypeEnum.CONTRACT)
                .chunk("Cobertura de vidros incluída.", fakeEmbeddings.fixo("vidros"))
                .gravar();
        data.documento(clienteB, "contrato.pdf", DocumentTypeEnum.CONTRACT)
                .chunk("A franquia é de R$ 9.999,00.", pergunta)        // idêntico à pergunta
                .gravar();

        var resultados = chunkRepository.searchByClient(clienteA.id(), pergunta, 20);

        assertThat(resultados)
                .isNotEmpty()
                .allSatisfy(m -> assertThat(m.embedded().metadata().getLong("client_id"))
                        .isEqualTo(clienteA.id()));
        assertThat(resultados)
                .extracting(m -> m.embedded().text())
                .doesNotContain("A franquia é de R$ 9.999,00.");
    }
}
```

## 8. `ApiFlowIT` e `SampleDocumentsIT`

| Cenário | Classe | Fluxo |
|---|---|---|
| CT-023 | `ApiFlowIT` | Admin cria cliente → `GET /clients/{id}` com chave de admin → 200 com `name` |
| CT-083 | `ApiFlowIT` | Depois de criar o cliente, `SELECT api_key_hash FROM client` ≠ chave devolvida e = SHA-256 dela |
| CT-063 | `ApiFlowIT` | Cliente recém-criado faz `POST /search` → 200 com `results` vazio |
| CT-100 | `SampleDocumentsIT` | Cada PDF de `documents/cliente-*/` (DDT) extrai texto não vazio com o `PdfTextExtractor` real |
| CT-101 | `SampleDocumentsIT` | Valores exclusivos do Cliente A (do gabarito) não aparecem no texto dos PDFs do Cliente B, e vice-versa |
| CT-102 | `SampleDocumentsIT` | `documents/gabarito.md` existe e tem as 5 perguntas para os 2 clientes |

Os PDFs e o `documents/gabarito.md` existem desde 2026-10-02 (T-601). O `SampleDocumentsIT` (T-603) usa o `PdfTextExtractor` real, então é escrito depois da T-405; não precisa de `@Disabled`. Os valores exclusivos do CT-101 são lidos da seção "Valores exclusivos" do gabarito, para que o teste acompanhe mudanças nos PDFs.

## 9. Opt-in com OpenAI real — `RagQualityOpenAiIT`

Não roda no `mvnw test`. Valida a qualidade de ponta a ponta com os documentos de exemplo.

```java
@Tag("openai")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RagQualityOpenAiIT { ... }
```

- Usa os beans reais (`OpenAiEmbeddingModel`, `RagAssistant` real), **sem** `AiTestConfig`.
- Banco: Testcontainers (dados limpos) — carrega os 6 PDFs pela API.
- Dados: `ddt/gabarito-perguntas.csv` ([04](04%20Testes%20orientados%20a%20dados.md), seção 3.8).
- Execução: `./mvnw test -Dgroups=openai -Dtest.excluded.groups=nenhum`.
- Custo: embeddings dos 6 PDFs + 10 perguntas com resposta; estimado em centavos de dólar.

| Cenário | Verificação |
|---|---|
| CT-035 | `similaridade("franquia da apólice", "valor da franquia")` > `similaridade("franquia da apólice", "data do sinistro")` |
| CT-075 | Para cada linha do gabarito, `answer` contém os termos esperados e `sources` cita o tipo de documento esperado |
| CT-076 | Pergunta fora dos documentos ("Qual a cor do carro do vizinho?") → resposta contém "não foi encontrada" e nenhum valor monetário |

Respostas de LLM variam: o critério é "contém termos esperados", nunca igualdade de texto. Falha isolada é reexecutada uma vez antes de virar BUG.
