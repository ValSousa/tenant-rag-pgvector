# RF-012 — Testes de isolamento entre clientes

## Objetivo

Comprovar, com testes automatizados, que uma consulta de um cliente nunca devolve chunks nem respostas com dados de outro cliente.

## Descrição

"Testes de isolamento entre clientes" é objetivo de aprendizado do README (seção 11), e a regra fundamental (seção 12) exige que um documento do Cliente B não seja retornado numa consulta autorizada só para o Cliente A. O README define JUnit e Mockito como ferramentas de teste. Hoje o projeto só tem o teste `contextLoads`.

## Atores

- Desenvolvedor (executa `mvnw test`).
- Pipeline de build, se houver.

## Pré-condições

- RF-002, RF-006 e RF-007 implementados.
- Banco PostgreSQL + pgvector disponível para testes de integração (RF-001 DP-03).

## Fluxo principal

1. O teste cria Cliente A e Cliente B.
2. O teste grava documentos e chunks para os dois pelo `PgVectorEmbeddingStore` real, com embeddings determinísticos (vetores fixos), sem chamar a OpenAI.
3. O teste grava no Cliente B um chunk com embedding idêntico ao da pergunta.
4. O teste executa a busca como Cliente A.
5. O teste verifica que nenhum resultado pertence ao Cliente B.

## Fluxos alternativos

- FA-01 — Cliente A sem documentos e Cliente B com documentos: a busca de A devolve lista vazia.
- FA-02 — Credencial de A acessando rota de B: 403 (RF-009).

## Regras de negócio

- RN-01 — O teste de isolamento roda contra PostgreSQL + pgvector real, porque a garantia está no SQL; mock de repositório não prova o filtro.
- RN-02 — Embeddings nos testes são fixos e controlados, para o resultado ser previsível.
- RN-03 — Testes unitários de serviço usam Mockito para `EmbeddingService` e repositórios; testes de integração usam um `EmbeddingModel` falso do LangChain4j.
- RN-04 — Os testes de isolamento fazem parte de `mvnw test`.

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| clientes, documentos e chunks de teste | dados criados pelo teste | Sim |
| vetores de embedding fixos | float[] com a dimensão configurada | Sim |

## Dados de saída

- Relatório do `mvnw test` com os casos de isolamento aprovados.

## Critérios de aceite

- CA-01 — Busca do Cliente A com chunk idêntico no Cliente B não devolve o chunk de B.
- CA-02 — Cliente sem documentos recebe lista vazia, mesmo com outros clientes populados.
- CA-03 — Acesso cruzado por credencial devolve 403 (quando RF-009 estiver implementado).
- CA-04 — Resposta gerada (RF-008) para o Cliente A não usa chunks de B (quando RF-008 estiver implementado).
- CA-05 — `mvnw test` executa todos esses casos sem exigir chave de API de provedor de IA.

## Dependências

- RF-001
- RF-002
- RF-006
- RF-007
- RF-009
- RF-008

## Situação atual do projeto

Atualizado em 2026-10-01:

- Infraestrutura de integração: `support/PostgresTestcontainersConfig` (pgvector/pgvector:pg17 + `docker/init.sql` lido pelo classpath) e `support/AbstractIntegrationTest` (contexto completo, MockMvc, perfil `test`, `TRUNCATE` antes de cada teste), reaproveitando contexto e contêiner entre classes.
- Dependências `testcontainers-postgresql` e `spring-boot-testcontainers` no `pom.xml`.
- `TestDataBuilder` (parte JPA, RF-002) e `FakeEmbeddingModel` + `AiTestConfig` (RF-004, CT-036) prontos.
- Atualizado em 2026-10-02 (T-504, com o RF-007): `TenantIsolationIT` e `ddt/isolation-matrix.csv` — busca de ponta a ponta (`POST /clients/{id}/search` com a chave do cliente → `SearchService` → `ChunkRepository` → PostgreSQL + pgvector do Testcontainers), com vetores fixos do `FakeEmbeddingModel` e o chunk do outro cliente idêntico à pergunta; o dono de cada resultado é conferido em `document_chunk.client_id`. CT-110 a CT-113 (P1) passando [evidência](../QA/evidencias/execucoes/2026-10-02-rf-007.md). `TestPdfFactory` já existe (RF-006). Faltam o CT-114 (`/ask`, T-704, com o RF-008), o CT-115 completo (matriz da T-303; a linha da busca já passa em `SearchControllerTest`) e o script `qa/atualizar-status-testes.py` (T-108).
- Atualizado em 2026-10-02 (T-704, com o RF-008): CT-114 (P1) em `TenantIsolationIT` — `POST /clients/{id}/ask` com a chave do Cliente A, Cliente B com um chunk idêntico à pergunta, PostgreSQL + pgvector real. O `RagAssistant` real (`AiServices`) roda sobre o `FakeChatModel` (`AiTestConfig`), que guarda o prompt exatamente como iria para o modelo: só os 8 trechos do Cliente A (topK padrão do `/ask`) chegam ao prompt, sem texto, valor ou arquivo do Cliente B, e as `sources` são de documentos do Cliente A (conferido em `document.client_id`). CA-04 coberto; a linha do `/ask` do CT-115 também passa (`AnswerControllerTest`) [evidência](../QA/evidencias/execucoes/2026-10-02-rf-008.md). Faltam o CT-115 completo (T-303) e a T-108.
- Atualizado em 2026-10-02 (T-303, com o RF-009): CT-115 (P1) completo em `SecurityConfigTest.naoDevePermitirAcessoCruzadoNasRotasDeDados` — chave do Cliente A nas rotas do Cliente B e vice-versa em search, documents e ask → 403 `ProblemDetail`, nenhum service chamado; as mesmas combinações estão na `security-matrix.csv` (CT-080) [evidência](../QA/evidencias/execucoes/2026-10-02-rf-009.md). Falta só a T-108.

## Itens a implementar

- Dependência de Testcontainers (módulo PostgreSQL) usando a imagem com pgvector (ADR-012) — **já adicionada no RF-001**.
- Classe de teste de integração do `ChunkRepository`/`SearchService` com os cenários acima.
- Testes unitários com Mockito para `DocumentService` e `SearchService`.

## Decisões pendentes

- DP-01 — Banco dos testes. **Decidido:** Testcontainers com `pgvector/pgvector:pg17`, contêiner temporário (ADR-012).

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-002 (P2) — Aplicação sobe com o contexto completo
- CT-036 (P2) — FakeEmbeddingModel é determinístico
- CT-110 (P1) — Chunk idêntico de outro cliente não aparece
- CT-111 (P1) — Cliente vazio não vê dados de outro
- CT-112 (P1) — topK alto não "completa" com dados de outro cliente
- CT-113 (P1) — Matriz de isolamento A↔B (DDT)
- CT-114 (P1) — Contexto da resposta só tem dados do cliente
- CT-115 (P1) — Acesso cruzado por credencial em todas as rotas de dados
- CT-128 (P1, manual) — Cliente sem documentos não vê nada dos outros
- CT-135 (P1, manual) — Acesso cruzado entre Cliente A e Cliente B devolve 403 em todas as rotas
- CT-136 (P1, manual) — Mesma pergunta, cada cliente só vê os próprios dados

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Em andamento
- Prontidão: pronto para desenvolvimento junto com RF-007.
- 2026-10-01: 3 de 7 tarefas concluídas (T-102, T-106, T-404).
- 2026-10-02: 4 de 7 tarefas concluídas (+ T-504); CT-002, CT-036 e CT-110 a CT-113 passando; faltam T-108, T-303 (CT-115) e T-704 (CT-114, com o RF-008).
- 2026-10-02 (RF-008): 5 de 7 tarefas concluídas (+ T-704); CT-114 passando; faltam T-108 e T-303 (CT-115).
- 2026-10-02 (RF-009): 6 de 7 tarefas concluídas (+ T-303); todos os 8 cenários passando (CT-115 completo); falta a T-108 (script de status).
