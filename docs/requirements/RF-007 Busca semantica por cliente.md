# RF-007 — Busca semântica por cliente

## Objetivo

Receber uma pergunta de um cliente e devolver os chunks mais parecidos, buscando somente nos documentos desse cliente.

## Descrição

O README (seções 6, 7 e 9) define o endpoint `POST /clients/{clientId}/search` no `SearchController` e o fluxo do `SearchService`: pergunta → embedding → filtro `clientId` → pgvector → top-K chunks. A consulta conceitual filtra pelo cliente (`WHERE client_id = :clientId`), ordena por `embedding <=> :queryEmbedding` (distância de cosseno) e limita a 5. Na arquitetura, a busca é feita pelo `PgVectorEmbeddingStore` do LangChain4j com filtro de metadado `client_id` (ADR-004). A regra de segurança (seção 12) proíbe separar clientes só pela similaridade.

## Atores

- Usuário do cliente que faz a pergunta (Não informado no README; ver RF-009).

## Pré-condições

- Cliente `{clientId}` cadastrado (RF-003).
- Requisição autorizada para o cliente (RF-009).
- Modelo de embeddings acessível (RF-004).

## Fluxo principal

1. O ator envia `POST /clients/{clientId}/search` com a pergunta.
2. O sistema confirma que o cliente existe.
3. O sistema gera o embedding da pergunta (RF-004).
4. O `ChunkRepository` executa a busca no `PgVectorEmbeddingStore` com o filtro `metadataKey("client_id").isEqualTo(clientId)`, que gera `WHERE client_id = :clientId`, ordenada por `<=>`, limitada a K.
5. O sistema responde `200 OK` com o `SearchResponseDTO`, do chunk mais parecido para o menos parecido.

## Fluxos alternativos

- FA-01 — Pergunta vazia ou ausente: `400 Bad Request`.
- FA-02 — `clientId` de cliente inexistente: recebe `403 Forbidden`, igual a qualquer `clientId` diferente do da chave, sem revelar se o cliente existe (RF-009 RN-03).
- FA-03 — Cliente sem documentos: `200 OK` com lista vazia, mesmo que outros clientes tenham documentos.
- FA-04 — Falha no modelo de embeddings: erro de serviço externo (RF-010).
- FA-05 — Acesso a cliente não autorizado: `403 Forbidden` (RF-009).

## Regras de negócio

- RN-01 — Toda busca vetorial filtra por `client_id` no próprio SQL; nunca filtrar depois, na aplicação.
- RN-02 — O `clientId` usado no filtro vem do caminho, validado pelo controle de acesso (RF-009).
- RN-03 — Ordenação por distância de cosseno (`<=>`), coerente com o índice `vector_cosine_ops`.
- RN-04 — K padrão igual a 5 (valor da consulta do README; ver DP-02).

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| clientId | número (caminho) | Sim |
| question | texto (SearchRequestDTO) | Sim |
| topK | número (SearchRequestDTO) | Não (ver DP-02) |

## Dados de saída

`SearchResponseDTO` (campos propostos, DP-01): lista de resultados com

- `chunkId` (UUID), `documentId`, `fileName`, `documentType`, `chunkIndex`, `content`, `score` (0 a 1; 1 = idêntico).

## Critérios de aceite

- CA-01 — "Qual é o valor da franquia da apólice?" feita pelo Cliente A devolve chunks do contrato do Cliente A.
- CA-02 — Nenhum resultado de uma busca do Cliente A pertence a documento do Cliente B (verificado em RF-012).
- CA-03 — Resultados vêm em ordem decrescente de `score`, no máximo K itens.
- CA-04 — Cliente sem documentos recebe lista vazia.
- CA-05 — O SQL executado (log do driver ou `EXPLAIN`) mostra o filtro por `client_id`.

## Dependências

- RF-002
- RF-003
- RF-004
- RF-006
- RF-009
- RF-010

## Situação atual do projeto

Atualizado em 2026-10-02:

- Implementado em 2026-10-02 (card HU-007, T-501 a T-503): `SearchController` (`POST /clients/{clientId}/search`), protegido pela regra `/clients/{clientId}/**` do `SecurityConfig` (só o próprio cliente; outro `clientId` → 403, sem chave → 401). `SearchRequestDTO` (`question` obrigatória, até 2000 caracteres; `topK` opcional de 1 a 20; fora disso → 400 em `ProblemDetail`).
- `SearchService` (sem transação): aplica o padrão 5 e o teto 20 (`app.rag.default-top-k`, `app.rag.max-top-k`), gera o embedding da pergunta pelo `EmbeddingService` (falha → 503) e converte os resultados em `SearchResultDTO` (`chunkId`, `documentId`, `fileName`, `documentType`, `chunkIndex`, `content`, `score`), na ordem do banco.
- `ChunkRepository.searchByClient(clientId, embedding, topK)` (`@Transactional(readOnly = true)`): executa `SET LOCAL hnsw.iterative_scan = strict_order` e chama o `PgVectorEmbeddingStore` com `metadataKey("client_id").isEqualTo(clientId)` e `minScore` 0; o SQL gerado tem `client_id::bigint = <clientId>` no `WHERE` e `ORDER BY embedding <=> <pergunta> LIMIT topK`.
- O passo 2 do fluxo principal (confirmar que o cliente existe) é garantido pelo controle de acesso: só a chave do próprio cliente passa, e o cliente da chave existe.
- Testes: CT-060 a CT-066 e CT-110 passando (`ChunkRepositoryTest`, `ChunkRepositoryIT`, `SearchServiceTest`, `SearchControllerTest`, `ApiFlowIT`, `TenantIsolationIT`); massas `question-validation.csv` e `topk-validation.csv`. `mvnw verify` com 149 de 149 [evidência](../QA/evidencias/execucoes/2026-10-02-rf-007.md).

## Itens a implementar

- `SearchController` (`POST /clients/{clientId}/search`).
- `SearchService` com o fluxo do README.
- `ChunkRepository.searchByClient` sobre o `PgVectorEmbeddingStore`, com filtro obrigatório por `client_id`.
- `SearchRequestDTO` e `SearchResponseDTO` em `br.com.rag_pgvector.dto`.

## Decisões pendentes

- DP-01 — Campos de `SearchRequestDTO` e `SearchResponseDTO`. **Definido na arquitetura (05):** `question` e `topK`; resultados com os campos acima.
- DP-02 — K. **Definido na arquitetura (05):** `topK` opcional, padrão 5, aceito de 1 a 20; fora disso, 400.
- DP-03 — Limiar mínimo de similaridade. **Definido na arquitetura (04):** sem limiar (`minScore = 0`).

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-060 (P2) — Busca devolve chunks do cliente ordenados por relevância
- CT-061 (P2) — Validação e padrão de topK (DDT)
- CT-062 (P2) — Validação da pergunta (DDT)
- CT-063 (P2) — Cliente sem documentos recebe lista vazia
- CT-064 (P2) — Falha no embedding da pergunta
- CT-065 (P1) — O filtro client_id é sempre enviado ao store
- CT-066 (P3) — Faixa e significado do score
- CT-110 (P1) — Chunk idêntico de outro cliente não aparece
- CT-127 (P2, manual) — Busca do Cliente A pelo Swagger UI
- CT-128 (P1, manual) — Cliente sem documentos não vê nada dos outros
- CT-133 (P1, manual) — Falha da OpenAI vira 503 sem detalhes técnicos e sem gravação parcial
- CT-136 (P1, manual) — Mesma pergunta, cada cliente só vê os próprios dados

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Concluído — entregue na v0.1.0 (Produção, 2026-10-06).
- Prontidão: pronto para desenvolvimento (modelo em ADR-006 e acesso em ADR-008, ambas aprovadas).
- 2026-10-02: T-501 a T-503 concluídas (com T-504 e T-505); todos os cenários passando, inclusive os P1 CT-065 e CT-110 [evidência](../QA/evidencias/execucoes/2026-10-02-rf-007.md).
