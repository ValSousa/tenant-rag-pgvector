# Como testar

Guia para rodar os testes automatizados. Vale para o que já foi entregue até o RF-006 (atualizado em 2026-10-02). A estratégia completa está em [QA 01 — Estratégia de testes](../QA/01%20Estrategia%20de%20testes.md).

## 1. Pré-requisitos

- `JAVA_HOME` apontando para o JDK 21 (ver [Como executar, seção 1](Como%20executar.md)).
- Docker no ar. Os testes de integração (`*IT`) sobem um PostgreSQL + pgvector temporário com Testcontainers (imagem `pgvector/pgvector:pg17`, o mesmo `docker/init.sql` do `docker-compose.yml`).
- **Não** é preciso subir o `docker compose` nem definir `OPENAI_API_KEY` ou `ADMIN_API_KEY`.

## 2. Rodar todos os testes

```bash
./mvnw test
```

Windows (PowerShell):

```powershell
.\mvnw.cmd test
```

O `mvnw test` nunca chama a OpenAI:

- os testes usam o perfil `test` (`src/test/resources/application-test.properties`), com chaves falsas;
- o `EmbeddingModel` é trocado pelo `FakeEmbeddingModel` (`support/AiTestConfig`), que gera vetores fixos de 768 dimensões;
- o teste com a OpenAI real (`RagQualityOpenAiIT`) tem a tag `openai`, excluída no `pom.xml` (`test.excluded.groups`).

Hoje são 112 testes (execução de 2026-10-02), em cerca de 2 minutos.

O Surefire roda as classes `*Test`, `*Tests` e `*IT`. A JVM dos testes usa `-Xmx384m -XX:+UseSerialGC` (configurado no `pom.xml`) para caber em máquina com pouca memória livre.

## 3. Rodar uma classe ou um método

```bash
./mvnw test -Dtest=ClientControllerTest
./mvnw test -Dtest=ClientControllerTest#deveCadastrarClienteComChaveDeAdministrador
./mvnw test -Dtest='*IT'                 # só integração
```

Classes de teste existentes hoje:

| Classe | Tipo | Precisa de Docker |
|---|---|---|
| `security/ApiKeyHasherTest` | Unitário | Não |
| `service/ClientServiceTest` | Unitário | Não |
| `service/EmbeddingServiceTest` | Unitário | Não |
| `service/DocumentServiceTest` | Unitário | Não |
| `service/DocumentWriterTest` | Unitário | Não |
| `repository/ChunkRepositoryTest` | Unitário | Não |
| `service/SearchServiceTest` | Unitário | Não |
| `service/AnswerServiceTest` | Unitário | Não |
| `security/ClientAccessAuthorizationManagerTest` | Unitário | Não |
| `ingestion/PdfTextExtractorTest` | Unitário (PDFs gerados pelo `TestPdfFactory`) | Não |
| `ingestion/TextChunkerTest` | Unitário | Não |
| `validator/DocumentFileValidatorTest` | Unitário (regras do arquivo do upload) | Não |
| `support/FakeEmbeddingModelTest` | Unitário | Não |
| `logging/RequestLoggingFilterTest` | Unitário (linha de log por requisição, trace ID, nível; log lido com `OutputCaptureExtension`) | Não |
| `logging/LoggingConfigurationTest` | Unitário (nenhum `DEBUG`/`TRACE` de cabeçalhos, SQL com parâmetros ou da OpenAI nas propriedades) | Não |
| `controller/ClientControllerTest` | Web (MockMvc, `WebSliceTest`) | Não |
| `controller/DocumentControllerTest` | Web (MockMvc, `WebSliceTest`) | Não |
| `controller/SearchControllerTest` | Web (MockMvc, `WebSliceTest`) | Não |
| `controller/AnswerControllerTest` | Web (MockMvc, `WebSliceTest`) | Não |
| `exception/GlobalExceptionHandlerTest` | Web (MockMvc, `WebSliceTest`) | Não |
| `config/SecurityConfigTest` | Web (MockMvc, `WebSliceTest`; matriz `ddt/security-matrix.csv`) | Não |
| `logging/RequestLoggingWebTest` | Web (MockMvc, `WebSliceTest`; matriz `ddt/request-log-matrix.csv`, linha de log nos 2xx, 401, 403 e 404) | Não |
| `SampleDocumentsIT` | Integração sem Spring: lê os PDFs de `documents/` e confere com `documents/gabarito.md` | Não |
| `TenantRagPgvectorApplicationIT` | Integração (`AbstractIntegrationTest`) | Sim |
| `SchemaMigrationIT` | Integração | Sim |
| `EntityMappingIT` | Integração | Sim |
| `ApiFlowIT` | Integração | Sim |
| `ChunkRepositoryIT` | Integração (chunks no pgvector) | Sim |
| `DocumentIngestionIT` | Integração (upload ponta a ponta, reenvio, rollback) | Sim |
| `UploadLimitIT` | Integração com servidor real e a configuração real do `application.properties` (limite de 5 MB e `server.tomcat.max-swallow-size`, que o MockMvc não aplica) | Sim |
| `TenantIsolationIT` | Integração (isolamento entre clientes na busca e no `/ask`, massa `ddt/isolation-matrix.csv`) | Sim |
| `RequestLoggingIT` | Integração (nenhuma chave, hash, senha do banco, pergunta ou nome de arquivo no log) | Sim |
| `RagQualityOpenAiIT` | Opt-in, OpenAI real (tag `openai`) | Não (só a OpenAI) |

Todas ficam em `src/test/java/br/com/rag_pgvector/`. Cada teste traz o ID do cenário no `@DisplayName` (ex.: `CT-020 — ...`); o catálogo está em [QA 05 — Cenários de teste](../QA/05%20Cenarios%20de%20teste.md). Massas de dados (DDT) ficam em `src/test/resources/ddt/`. Os PDFs dos testes unitários e de integração são gerados em memória pelo `support/TestPdfFactory`; só o `SampleDocumentsIT` usa os PDFs de `documents/`.

## 4. Teste opt-in com a OpenAI real

O `RagQualityOpenAiIT` (hoje só o CT-035, do RF-004) chama a OpenAI de verdade: gasta tokens e precisa de `OPENAI_API_KEY`. Não precisa de Docker nem de `ADMIN_API_KEY`. Sem a variável, o teste é ignorado.

Linux/macOS (bash):

```bash
export OPENAI_API_KEY=sk-...
./mvnw test -Dgroups=openai -Dtest.excluded.groups=nenhum -Dtest=RagQualityOpenAiIT
```

Windows (PowerShell; as aspas evitam que o PowerShell quebre os argumentos com ponto):

```powershell
$env:OPENAI_API_KEY = "sk-..."
.\mvnw.cmd test "-Dgroups=openai" "-Dtest.excluded.groups=nenhum" "-Dtest=RagQualityOpenAiIT"
```

`-Dtest.excluded.groups=nenhum` substitui a exclusão padrão da tag `openai`.

## 5. Cobertura (JaCoCo)

```bash
./mvnw verify
```

Roda os testes e gera o relatório em `target/site/jacoco/index.html`. O `mvnw test` sozinho não gera o relatório HTML.

## 6. Onde ficam os resultados

| O quê | Onde | Versionado |
|---|---|---|
| Relatórios do Surefire (por classe, XML e texto) | `target/surefire-reports/` | Não |
| Relatório de cobertura | `target/site/jacoco/index.html` | Não |
| Evidências de execução (resumo por execução relevante, testes manuais e opt-in) | [`docs/QA/evidencias/`](../QA/evidencias/README.md) | Sim |
| Status de cada cenário `CT-xxx` | Dashboard, `Testes/Testes.json` | — |

As regras de quando registrar evidência estão em [QA 01, seção 11.3](../QA/01%20Estrategia%20de%20testes.md).

## 7. Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `Could not find a valid Docker environment` | Docker parado | Inicie o Docker e rode de novo |
| Erro de compilação com `release 21` ou classe de versão | `JAVA_HOME` não é o JDK 21 | Ajuste o `JAVA_HOME` ([Como executar, seção 1](Como%20executar.md)) |
| `RagQualityOpenAiIT` aparece como ignorado | `OPENAI_API_KEY` não definida no terminal | Defina a variável e use o comando da seção 4 |
| No PowerShell, o Maven reclama de fase ou argumento desconhecido como `.excluded.groups=nenhum` | O PowerShell separou o argumento no ponto | Coloque cada `-D...` entre aspas |

## Veja também

- [Como executar](Como%20executar.md)
- [QA 03 — Testes de integração](../QA/03%20Testes%20de%20integracao.md)
- [Arquitetura 08 — Estratégia de testes](../arquitetura/08%20Estrategia%20de%20testes.md)
