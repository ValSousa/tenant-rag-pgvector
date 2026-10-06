# Revisão de código — RF-002

- **Data:** 2026-10-05 (reconferência depois dos testes manuais CT-117 e CT-118); revisões anteriores: 2026-10-02 e 2026-10-01
- **Escopo:** alterações não commitadas sobre o commit `997f680` (branch `feature/ingestao-documentos`): `db/migration/V1__create_tables.sql`, `entity/ClientEntity`, `entity/DocumentEntity`, `enums/DocumentTypeEnum`, `repository/ClientRepository`, `repository/DocumentRepository`; testes `SchemaMigrationIT`, `EntityMappingIT`, `support/TestDataBuilder`, `support/Fixtures`, `support/DdtValores`, `ddt/document-type-check.csv`. Na reconferência de 2026-10-02: `repository/ChunkRepository`, `config/AiConfig` (`columnDefinitions`), `service/DocumentWriter` (uso do `DocumentRepository`), `ChunkRepositoryIT` e `repository/ChunkRepositoryTest` (CT-051). Na reconferência de 2026-10-05: os mesmos arquivos, a busca por leituras de `document` em `src/main` e as evidências dos testes manuais CT-117 e CT-118
- **Tarefas cobertas:** T-104, T-105, T-106 (parte JPA), T-107; CT-051 via T-407 (RF-006); card HU-002 (testes CT-002, CT-010 a CT-016, CT-051, CT-117, CT-118)
- **Revisores:** Claude Code (agente revisor-de-codigo), a pedido do responsável pelo projeto
- **Resultado:** nenhum defeito no esquema nem no mapeamento. A migration é igual à da [arquitetura 04](../arquitetura/04%20Modelo%20de%20dados.md), seção 3, e o cenário P1 (CT-013) prova a FK composta. A pendência da revisão anterior está resolvida: o CT-051 existe (`ChunkRepositoryIT`, `ChunkRepositoryTest`) e passa, e as `columnDefinitions` do `AiConfig` têm os mesmos nomes e tipos da V1. O achado 1 (leituras herdadas sem `clientId`) não será corrigido: o código de produção só usa `save` e `deleteByClientIdAndFileName` (filtrado pelo cliente), então não há caminho de vazamento entre clientes. Reconferência de 2026-10-05: nenhum arquivo do RF-002 mudou desde a aprovação de 2026-10-02; nenhuma leitura nova de `document` em produção; nenhum achado novo; os testes manuais CT-117 e CT-118 (P1) estão OK.
- **Aprovado para fechamento:** Sim — reconfirmado em 2026-10-05: código sem mudança desde a aprovação de 2026-10-02, todos os testes do card OK (automáticos e manuais CT-117/CT-118); nenhum achado aberto; o achado 1 (Baixa) não será corrigido porque nenhum código de produção lê `document` pelos métodos herdados.

## Achados

| # | Gravidade | Onde | Problema | Correção sugerida | Status |
|---|---|---|---|---|---|
| 1 | Baixa | `src/main/java/br/com/rag_pgvector/repository/DocumentRepository.java:9` | O repository estende `JpaRepository` e herda `findById`, `findAll`, `existsById` e `deleteById`, que leem `document` sem `clientId`. A regra 1 da [07, seção 3](../arquitetura/07%20Seguranca%20e%20isolamento.md) diz que todo método de repository que lê `document` recebe `clientId` e o usa no `WHERE`. Hoje nenhum código de produção chama esses métodos (só o `EntityMappingIT` usa `findById`), então não há vazamento. | Ao criar o `DocumentService`/`DocumentWriter` (RF-006), declarar as leituras com cliente (ex.: `Optional<DocumentEntity> findByIdAndClientId(Long id, Long clientId)`) e não usar os métodos herdados no código de produção; se possível, estender `Repository`/`ListCrudRepository` só com `save` e as consultas com cliente | Não será corrigido — reconferido em 2026-10-02: o único uso em produção é o `DocumentWriter.save` (linhas 42 e 46), que chama `deleteByClientIdAndFileName(clientId, fileName)` (JPQL com `d.client.id = :clientId`) e `save`; nenhum `findById`/`findAll`/`existsById`/`deleteById` em `src/main`, e nenhum RF planejado lê `document` por id (a busca e a resposta leem `document_chunk` pelo `ChunkRepository`, com filtro de `client_id`). Trocar a interface base é hardening sem ganho real neste projeto de estudo; a regra 1 continua valendo para qualquer leitura nova. Reconferido em 2026-10-05: `DocumentRepository` sem mudança desde 2026-10-02 e o único uso em produção continua sendo o `DocumentWriter` (linhas 42 e 46); nenhuma leitura nova de `document` (nem por JPA, nem por SQL nativo/`JdbcTemplate`) |
| 2 | Baixa | `src/test/java/br/com/rag_pgvector/EntityMappingIT.java:27` | O teste usa o ID `CT-016`, que não existe no [catálogo de cenários](../QA/05%20Cenarios%20de%20teste.md), na lista "Cenários de teste (QA)" do RF-002 nem no `Testes.json` do dashboard. A parte JPA do CA-04 ("as entidades leem e gravam sem erro") fica coberta por um cenário que a rastreabilidade não enxerga. | O analista de QA registra o CT-016 (nível I, P2, `EntityMappingIT`, cobre RF-002 CA-04 parte JPA) no catálogo, na rastreabilidade, no RF-002 e no `Testes.json` | Corrigido (2026-10-01) |

## Pendências

- **RF-006 (T-407):** CT-051 (metadados gravados pelo `PgVectorEmbeddingStore`). Resolvida em 2026-10-02: `ChunkRepositoryIT` grava 3 chunks pelo store real e confere no banco `embedding_id`, `text`, `client_id`, `document_id`, `chunk_index` 0..2, `document_type`, `file_name` e `vector_dims = 768`; `ChunkRepositoryTest` confere os metadados enviados e a recusa de documento de outro cliente. As `columnDefinitions` do `AiConfig` (linhas 35-39) são iguais às colunas da V1 (linhas 35-39).
- **RF-006:** achado 1 (leituras de `document` com `clientId`). Resolvida em 2026-10-02: o RF-006 não criou leitura de `document`; o achado fica como "Não será corrigido".

## Pontos conferidos sem problema

- `V1__create_tables.sql` é igual ao DDL da arquitetura 04, seção 3: `CREATE EXTENSION IF NOT EXISTS vector` (resolve a pendência da revisão do RF-001), `VECTOR(768) NOT NULL`, `CHECK ck_document_type`, `UNIQUE (id, client_id)` como alvo da FK composta `fk_chunk_document_client`, `ON DELETE CASCADE` só em `document_chunk`, `UNIQUE (document_id, chunk_index)`, índices em `client_id` e índice HNSW `vector_cosine_ops`.
- `ClientEntity` e `DocumentEntity` com `@Table` explícito, `@Enumerated(EnumType.STRING)`, `@ManyToOne(fetch = LAZY, optional = false)`, `created_at` `updatable = false`, construtor protegido para o JPA e sem setters; `ddl-auto=validate` passa (CT-002).
- `DocumentTypeEnum` tem os mesmos três valores do `CHECK`; nomes seguem as convenções (`Entity`, `Enum` no pacote `enums`).
- `ClientRepository.findByApiKeyHash` busca pelo índice único `uk_client_api_key_hash`; o `ClientRepository` não lê documentos.
- `DocumentRepository.deleteByClientIdAndFileName` filtra pelo cliente no `WHERE` (nunca afeta outro cliente); os chunks saem pelo `ON DELETE CASCADE` da FK composta; `clearAutomatically`/`flushAutomatically` evitam estado velho no contexto de persistência.
- `SchemaMigrationIT`: CT-001, CT-010 a CT-015 conferem o que o catálogo descreve, inclusive o nome da constraint violada (`fk_document_client`, `fk_chunk_document_client`, `ck_document_type`). O CT-013 (P1) insere um chunk com o documento do Cliente A e o `client_id` do Cliente B e espera a violação da FK composta.
- CT-014 (DDT) cobre os três valores aceitos, um valor fora da lista, minúsculo e vazio; `DdtValores` trata `<null>`, `<vazio>`, `<espacos>` e `<N x>`.
- `EntityMappingIT` compara horários com tolerância de 1 µs (o `timestamptz` arredonda para microssegundos); sem dependência de horário, ordem ou rede.
- CT-051 (`ChunkRepositoryIT`) usa o `FakeEmbeddingModel` (vetores fixos), sem chamada à OpenAI; `@DisplayName` com o ID nos três testes.
- `TestDataBuilder` grava pelo caminho real (repositories) e gera hashes aleatórios de 64 caracteres, sem colidir com a `uk_client_api_key_hash`.
- Relatórios do Surefire de 2026-10-02 11:32 (mais novos que todos os fontes): `SchemaMigrationIT` 12 de 12, `EntityMappingIT` 1 de 1, `ChunkRepositoryIT` 1 de 1 e `ChunkRepositoryTest` 2 de 2 passando; o QA registrou `mvnw.cmd -B verify` com 112 testes e 0 falhas (`docs/QA/evidencias/execucoes/2026-10-02-997f680-rf-002-rf-004.md`).

## Reconferência de 2026-10-05

Motivo: o card HU-002 voltou de Concluído para Em teste, fase Manual, em 2026-10-02 (decisão do usuário) só para aguardar os testes manuais CT-117 e CT-118, executados e registrados como OK em 2026-10-05 ([CT-117](../QA/evidencias/CT-117/2026-10-05.md), [CT-118](../QA/evidencias/CT-118/2026-10-05.md)). O card está Em revisão.

O que foi conferido:

- **Código sem mudança desde a aprovação:** as datas de modificação de `V1__create_tables.sql`, `ClientEntity`, `DocumentEntity`, `DocumentTypeEnum`, `ClientRepository` (2026-10-01) e `DocumentRepository` (2026-10-02 10:26) são anteriores à revisão aprovada (2026-10-02 11:41). O conteúdo foi relido inteiro e é o descrito em "Pontos conferidos sem problema". Continua havendo uma única migration (`V1`).
- **Leituras de `document` (achado 1):** em `src/main`, o `DocumentRepository` só é usado pelo `DocumentWriter` (`deleteByClientIdAndFileName` e `save`). Os `findById` encontrados em `src/main` são do `ClientService`/`ClientController` (tabela `client`, já revisados no RF-003). O único `JdbcTemplate` de produção é o do `ChunkRepository`, que só executa o `SET LOCAL hnsw.iterative_scan`; o `searchByClient` (RF-007, alterado depois da aprovação do RF-002) lê `document_chunk` com `metadataKey("client_id").isEqualTo(clientId)` e não lê `document`. Não há `nativeQuery`, `EntityManager` nem `createQuery` em `src/main`. O achado 1 continua sem caminho de vazamento e fica como "Não será corrigido".
- **Testes automáticos:** relatórios do Surefire de 2026-10-02 19:58-19:59 (mais novos que todos os fontes do RF-002): `SchemaMigrationIT` 12 de 12, `EntityMappingIT` 1 de 1, `ChunkRepositoryIT` 3 de 3, `ChunkRepositoryTest` 4 de 4, `TenantRagPgvectorApplicationIT` (CT-002) passando; `@DisplayName` com o ID em CT-002, CT-010 a CT-016 e CT-051. Não reexecutados nesta reconferência.
- **Testes manuais:** CT-117 confere no banco local as tabelas, a linha da V1 no `flyway_schema_history`, o índice HNSW `vector_cosine_ops`, o índice de `client_id` e `vector(768)`; CT-118 (P1) mostra a FK `fk_chunk_document_client` recusando o chunk do documento do cliente 1 com `client_id` 2 e contagem 0. As duas evidências batem com o resultado esperado do roteiro.

Achados novos: nenhum.

## Resumo por área

| Área | Situação | Justificativa |
|---|---|---|
| Arquitetura | Aderente | Migration igual à arquitetura 04, seção 3; entidades e repositories nos pacotes e com os nomes das convenções; sem JPA para `document_chunk` (ADR-004). |
| Qualidade (análise estática) | Aderente | Arquivos relidos inteiros: sem imports não usados, `TODO`, código comentado, `System.out` ou código morto; entidades sem setters e com construtor protegido. |
| Segurança | Nenhum problema identificado | FK composta impede chunk com cliente diferente do documento (CT-013 e CT-118); a única operação em `document` além de `save` filtra pelo cliente; o achado 1 (Baixa) não tem uso em produção. |
| Testes | Aderente | CT-002, CT-010 a CT-016 e CT-051 com o ID no `@DisplayName` e passando; CT-117 e CT-118 manuais OK; P1 (CT-013, CT-118) não desabilitados; nenhum teste chama a OpenAI. |

**Próximo responsável:** `gerente-de-release` (aprovado; card HU-002 Concluído, aguardando release).
