# 08 — Estratégia de testes

> Este documento registra as **decisões técnicas** de teste (ADR-012). O plano completo de QA — riscos, cenários `CT-xxx`, massas de dados (DDT), critérios por etapa e rastreabilidade — está em [`docs/QA/`](../QA/README.md). Em caso de divergência nos detalhes de um cenário, vale o catálogo [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md).

## 1. Princípios

- `mvnw test` roda **sem chamar a OpenAI e sem `OPENAI_API_KEY` real**: embeddings nos testes são determinísticos e o chat é mockado (ADR-012).
- O isolamento entre clientes é testado **contra PostgreSQL + pgvector real** (Testcontainers), usando o `PgVectorEmbeddingStore` de verdade, porque a garantia está no SQL que ele gera. Mock de repository ou de `EmbeddingStore` não prova o filtro (RF-012 RN-01).
- Docker precisa estar rodando para os testes de integração.

## 2. Níveis

| Nível | Ferramentas | Alvo | Exemplos |
|---|---|---|---|
| Unitário | JUnit 5, Mockito, AssertJ | `TextChunker`, `PdfTextExtractor`, `ApiKeyHasher`, services com dependências mockadas | `DocumentServiceTest`: falha no embedding não chama `DocumentWriter` |
| Web (slice) | `@WebMvcTest`, MockMvc, `@MockitoBean` | Controllers, validação, `GlobalExceptionHandler`, regras do `SecurityConfig` | Pergunta vazia → 400 `ProblemDetail`; chave de A em rota de B → 403 |
| Integração | `@SpringBootTest`, Testcontainers (`pgvector/pgvector:pg17`), `@ServiceConnection` | Migrations, `PgVectorEmbeddingStore` em `COLUMN_PER_KEY`, filtro por `client_id`, rollback da ingestão, isolamento ponta a ponta | `TenantIsolationIT`, `ChunkRepositoryIT` |
| Manual / exploratório | OpenAI real (`OPENAI_API_KEY`), documentos do RF-011 | Qualidade das respostas às perguntas da seção 10 | Gabarito em `documents/gabarito.md` |

## 3. Embeddings determinísticos

`FakeEmbeddingModel` (em `src/test/java`) implementa a interface `EmbeddingModel` do LangChain4j e é registrado como `@Primary` numa `@TestConfiguration`, no lugar do `OpenAiEmbeddingModel`:

- Para textos conhecidos do teste, devolve vetores escolhidos à mão (por exemplo, eixo 0 = "franquia", eixo 1 = "vistoria").
- Para qualquer outro texto, gera um vetor de 768 posições a partir do hash do texto (mesmo texto → mesmo vetor) e normaliza.

Assim o teste controla exatamente qual chunk é "mais parecido".

## 4. Testcontainers (Spring Boot 4)

Implementado no RF-001 em `src/test/java/br/com/rag_pgvector/support/PostgresTestcontainersConfig.java`:

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

- No Testcontainers 2 (usado pelo Spring Boot 4) a classe é `org.testcontainers.postgresql.PostgreSQLContainer`, sem parâmetro genérico; o artefato é `org.testcontainers:testcontainers-postgresql`.
- O contêiner recebe o mesmo `docker/init.sql` do `docker-compose.yml`, então o banco de teste nasce igual ao local (extensão `vector` criada antes de qualquer migration).
- Tempo medido: cerca de 28 s para subir o contêiner na primeira classe; as seguintes reaproveitam contexto e contêiner.

Reaproveitar o contêiner entre classes de teste (mesma configuração importada em todas) para não subir um banco por classe.

## 5. Casos obrigatórios de isolamento (`TenantIsolationIT`)

Montagem: Cliente A e Cliente B, cada um com um documento; vetores fixos.

| # | Cenário | Esperado |
|---|---|---|
| 1 | Cliente B tem um chunk com embedding **idêntico** ao da pergunta; Cliente A tem chunks pouco parecidos. Busca como A. | Só chunks de A; nenhum `documentId` de B |
| 2 | Cliente A sem documentos; B com documentos. Busca como A. | Lista vazia |
| 3 | `POST /clients/{B}/search` com chave de A | 403; nenhum SQL de busca executado (verificar com `@MockitoSpyBean` no repository ou contagem de chamadas) |
| 4 | `POST /clients/{B}/documents` com chave de A | 403; nenhum documento gravado para B |
| 5 | `POST /clients/{A}/ask` com `RagAssistant` mockado | O `context` enviado ao mock só contém texto de chunks de A |
| 6 | `topK = 20`, 3 chunks em A e 50 em B | No máximo 3 resultados, todos de A |

## 6. Outros testes por componente

| Componente | Casos |
|---|---|
| `TextChunker` | texto curto → 1 chunk índice 0; índices sem lacuna; nenhum chunk vazio; nenhum chunk acima de `chunk-size` (contrato do wrapper; o algoritmo é do `DocumentSplitters.recursive`) |
| `PdfTextExtractor` | PDF de exemplo → texto não vazio; bytes inválidos → `InvalidDocumentException`; PDF só com imagem → `InvalidDocumentException` |
| `DocumentService` | fluxo principal grava N chunks; cliente inexistente → 404 sem chamar extrator; erro no embedding → nada gravado |
| `SearchService` | `topK` nulo → 5; `topK` 50 → 20; repassa `clientId` sem alterar; converte `EmbeddingMatch` em `SearchResultDTO` com `score` |
| `ChunkRepository` (IT) | metadados gravados nas colunas certas; busca sempre com filtro `client_id`; `client_id` diferente do documento é rejeitado pela FK composta |
| `AnswerService` | sem chunks → não chama `RagAssistant`; com chunks → monta contexto numerado e fontes |
| Migrations | sobem em banco vazio; `ddl-auto=validate` passa |
| Swagger / `SecurityConfig` | `/v3/api-docs` e `/swagger-ui.html` respondem 200 sem chave; especificação contém o esquema `apiKey` e os 5 endpoints; endpoints de dados sem chave → 401 |
| `DocumentFileValidator` (unitário, sem Spring) | arquivo vazio, nome ausente, nome com 256 caracteres, arquivo que não é PDF → `InvalidFileException` com a mensagem de [02](02%20Componentes%20e%20camadas.md), seção 3.2.1; PDF só pelo content-type ou só pela extensão → aceito; caminho no nome → devolve só o nome do arquivo (ADR-014) |
| `GlobalExceptionHandler` | 400 (inclusive `InvalidFileException`), 404, 413, 422, 503, 500 no formato `ProblemDetail`, sem stack trace |

### 6.1 Log por requisição (ADR-015, em revisão)

Para quando o RF-013 for implementado; os cenários `CT-xxx` ficam a cargo do analista de QA. O log é lido com o `OutputCaptureExtension` do Spring Boot (`org.springframework.boot.test.system`, no `spring-boot-test-4.1.1.jar` que já está no projeto): `@ExtendWith(OutputCaptureExtension.class)` e um parâmetro `CapturedOutput output` no teste. O `ConsoleAppender` do Logback escreve em `System.out` a cada linha, então a captura pega o log do contexto Spring. Nada chama a OpenAI: o `FakeEmbeddingModel` e o `FakeChatModel` continuam valendo. Sem dependência nova.

| Nível | Onde | O que verificar |
|---|---|---|
| Unitário, sem Spring | `RequestLoggingFilterTest` com `MockHttpServletRequest`, `MockHttpServletResponse` e `MockFilterChain` (ou uma cadeia que muda o status / lança exceção) | Uma linha com os dez campos; `endpoint` sem query string; `clientId` do caminho e `-` sem cliente; `status=500` e exceção relançada quando a cadeia falha; `traceId` com 32 caracteres hexadecimais, igual ao cabeçalho `X-Trace-Id` e diferente entre duas chamadas; MDC limpo depois da chamada; nível `INFO`/`WARN`/`ERROR` conforme o status (DP-07); rotas do Swagger sem linha |
| Web (slice) | `WebSliceTest` (o filtro é `@Component` do tipo `Filter`, então o `@WebMvcTest` já o inclui) e `GlobalExceptionHandlerTest` | `GET /clients/{id}` do dono → linha com `status=200` e `clientId`; sem chave → `status=401`; chave de outro cliente → `status=403`; o `ThrowingController` de teste gera 500 → a linha da requisição e a linha de `ERROR` do `GlobalExceptionHandler` trazem o mesmo `[traceId]`; corpo e status da resposta iguais aos de antes |
| Integração | Um `*IT` sobre o `AbstractIntegrationTest` (contexto compartilhado), passando pela API inteira | Com as chaves conhecidas do teste (admin `test-admin-key`, chave e hash de um cliente criado, `test-key-nao-usada` da OpenAI e a senha do banco), nenhuma linha capturada contém esses valores; as linhas de `/ask` e `/documents` não contêm a pergunta, o texto nem o nome do arquivo |

Cuidados:

- O teste de segredos precisa de valores que não apareçam por acaso no log. A senha padrão do contêiner Testcontainers é curta e genérica; o QA deve usar uma senha distinta no contêiner de teste (ex.: `.withPassword(...)` no `PostgresTestcontainersConfig`) ou checar só os segredos que têm valor distinto.
- Comparar com `contains`/`doesNotContain` no texto capturado; não depender do formato do prefixo do Logback (data, PID, thread), que muda entre ambientes.
- Se a DP-02 escolher JSON, os testes passam a ler o campo `message` e o campo `traceId` do JSON; a lógica dos casos não muda.

## 7. Convenções

- Unitários: sufixo `Test`. Integração: sufixo `IT`, rodando no mesmo `mvnw test` (Surefire configurado para incluir `**/*IT.java`) — o projeto é pequeno e o Testcontainers reaproveitado mantém o tempo baixo.
- Cada teste de integração cria os próprios dados e não depende da ordem de execução.
