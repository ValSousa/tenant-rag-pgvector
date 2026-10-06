# RF-002 — Estrutura do banco e índice vetorial

## Objetivo

Criar, por migration versionada, as tabelas `client`, `document` e `document_chunk` (tabela do `PgVectorEmbeddingStore` do LangChain4j), o índice vetorial HNSW e o mapeamento JPA de `client` e `document`.

## Descrição

O README (seções 5 e 6) define o modelo `CLIENT 1:N DOCUMENT 1:N DOCUMENT_CHUNK`, o DDL de cada tabela e o índice `hnsw (embedding vector_cosine_ops)`. A seção 3 prevê a migration `db/migration/V1__create_tables.sql` e as entidades `ClientEntity` e `DocumentEntity` (seção 7); os chunks são gravados pelo `PgVectorEmbeddingStore` do LangChain4j, sem entidade JPA. Nada disso existe no projeto.

## Atores

- Aplicação (executa as migrations na inicialização).
- Desenvolvedor.

## Pré-condições

- RF-001 concluído (banco com extensão `vector`).
- Dimensão do vetor definida: 768 (RF-004, ADR-006).

## Fluxo principal

1. A aplicação inicia e o Flyway verifica as migrations pendentes.
2. O Flyway executa `V1__create_tables.sql`, criando `client`, `document`, `document_chunk` e o índice HNSW.
3. O Hibernate valida que as entidades batem com o esquema.
4. A aplicação sobe pronta para gravar e ler dados.

## Fluxos alternativos

- FA-01 — Migration já aplicada: o Flyway não executa de novo.
- FA-02 — Esquema diferente das entidades: a validação falha e a aplicação não sobe, indicando a divergência.
- FA-03 — Dimensão do embedding diferente da coluna: a gravação de chunks falha (tratada em RF-006).

## Regras de negócio

- RN-01 — `document.client_id` é obrigatório e referencia `client(id)`.
- RN-02 — `document_chunk.document_id` e `document_chunk.client_id` são obrigatórios e referenciam juntos `document(id, client_id)`, garantindo que o cliente do chunk é sempre o do documento.
- RN-03 — `document_chunk.embedding` é `VECTOR(768)`, a dimensão configurada no `text-embedding-3-small` (ADR-006; o 1536 do README é só exemplo).
- RN-04 — Índice `document_chunk_embedding_idx` usando `hnsw (embedding vector_cosine_ops)`.
- RN-05 — O esquema só muda por novas migrations; nunca por `ddl-auto` de criação.
- RN-06 — `document_type` aceita os valores `CONTRACT`, `CLAIM`, `INSPECTION` (ver DP-02).

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| client.name | VARCHAR(255) | Sim |
| document.client_id | BIGINT | Sim |
| document.file_name | VARCHAR(255) | Sim |
| document.document_type | VARCHAR(100) | Sim |
| document.created_at | TIMESTAMP | Sim |
| document_chunk.embedding_id | UUID | Sim |
| document_chunk.embedding | VECTOR(768) | Sim |
| document_chunk.text | TEXT | Sim |
| document_chunk.client_id | BIGINT | Sim |
| document_chunk.document_id | BIGINT | Sim |
| document_chunk.chunk_index | INTEGER | Sim |
| document_chunk.document_type | VARCHAR(100) | Sim |
| document_chunk.file_name | VARCHAR(255) | Sim |

## Dados de saída

- Tabelas e índice criados; histórico registrado em `flyway_schema_history`.

## Critérios de aceite

- CA-01 — Em banco vazio, a aplicação sobe e cria as três tabelas e o índice HNSW.
- CA-02 — A consulta ao catálogo do PostgreSQL (`format_type` da coluna `document_chunk.embedding`) devolve `vector(768)`.
- CA-03 — Inserir `document` com `client_id` inexistente falha por chave estrangeira.
- CA-04 — As entidades `ClientEntity` e `DocumentEntity` leem e gravam nas tabelas sem erro, e o `PgVectorEmbeddingStore` grava e lê `document_chunk`.
- CA-05 — Inserir chunk com `client_id` diferente do `client_id` do documento falha por chave estrangeira.

## Dependências

- RF-001
- RF-004 (dimensão do vetor)

## Situação atual do projeto

Atualizado em 2026-10-01, após a implementação (T-104 a T-107):

- Migration `V1__create_tables.sql`: tabelas `client`, `document` e `document_chunk` (tabela do `PgVectorEmbeddingStore`, `VECTOR(768)`, colunas de metadados), índice HNSW `vector_cosine_ops`, `CHECK` de `document_type` e FK composta `(document_id, client_id)`.
- `ClientEntity`, `DocumentEntity`, `DocumentTypeEnum`, `ClientRepository` e `DocumentRepository`; a aplicação sobe com `ddl-auto=validate`.
- Testes: `SchemaMigrationIT` (CT-001, CT-010 a CT-015) e `EntityMappingIT` (CT-016) passando; apoio de teste `TestDataBuilder` (parte JPA), `Fixtures`, `DdtValores` e `TRUNCATE` na classe base.
- CT-051 (metadados gravados pelo `PgVectorEmbeddingStore`) passando desde 2026-10-02, com o `ChunkRepository` da T-407 (RF-006): `ChunkRepositoryIT` e `ChunkRepositoryTest`.

## Itens a implementar

- Dependências: `spring-boot-starter-flyway` e `flyway-database-postgresql` — **já adicionadas no RF-001**.
- `src/main/resources/db/migration/V1__create_tables.sql` com o DDL das seções 5 e 6 do README (versão final em `docs/arquitetura/04 Modelo de dados.md`).
- Entidades `ClientEntity` e `DocumentEntity` em `br.com.rag_pgvector.entity` (sem entidade para `document_chunk`).
- Enum `DocumentTypeEnum` (`CONTRACT`, `CLAIM`, `INSPECTION`) em `br.com.rag_pgvector.enums`.
- Repositórios `ClientRepository` e `DocumentRepository` (JPA) em `br.com.rag_pgvector.repository`; o `ChunkRepository`, sobre o `PgVectorEmbeddingStore`, fica em RF-006 e RF-007.
- `spring.jpa.hibernate.ddl-auto=validate`.

## Decisões pendentes

- DP-01 — Dimensão do vetor. **Decidido:** 768 (ADR-006).
- DP-02 — `document_type`: lista fechada ou texto livre. **Definido na arquitetura (02, 04):** enum `DocumentTypeEnum` e `CHECK` no banco.
- DP-03 — `embedding` pode ser nulo? **Definido na arquitetura (04):** `NOT NULL`.
- DP-04 — Comportamento ao excluir. **Definido na arquitetura (04):** apagar documento apaga seus chunks (`ON DELETE CASCADE`); apagar cliente com documentos é bloqueado.
- DP-05 — Índices de cliente. **Definido na arquitetura (04):** índices em `document.client_id` e `document_chunk.client_id`.

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-002 (P2) — Aplicação sobe com o contexto completo
- CT-010 (P2) — Migration V1 cria tabelas e índice HNSW
- CT-011 (P2) — Coluna de embedding com 768 dimensões
- CT-012 (P2) — Documento com cliente inexistente é rejeitado
- CT-013 (P1) — Chunk não pode ter cliente diferente do documento
- CT-014 (P3) — Tipo de documento fora da lista é rejeitado pelo banco (DDT)
- CT-015 (P3) — Exclusões em cascata e bloqueadas
- CT-016 (P2) — Entidades JPA gravam e leem client e document
- CT-051 (P2) — Metadados do chunk gravados nas colunas
- CT-117 (P3, manual) — Tabelas, índice HNSW e coluna vector(768) no banco local
- CT-118 (P1, manual) — Banco rejeita chunk com cliente diferente do documento

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Concluído — entregue na v0.1.0 (Produção, 2026-10-06).
- Prontidão: implementado em 2026-10-01 (T-104 a T-107); CT-051 passando desde 2026-10-02; falta o fechamento do QA e da revisão de código.
- 2026-10-01: migration V1, entidades, enum e repositórios implementados; CT-002 e CT-010 a CT-015 passando ([evidência](../QA/evidencias/execucoes/2026-10-01-rf-002.md)). Falta o CT-051 (parte do `PgVectorEmbeddingStore` do CA-04), que depende do `ChunkRepository` da T-407 (RF-006).
- 2026-10-01: o CT-016 (`EntityMappingIT`) tinha uma comparação de horário instável (o PostgreSQL arredonda para microssegundos e o teste truncava); corrigida com tolerância de 1 µs. Todos os cenários do RF-002 continuam passando ([evidência](../QA/evidencias/execucoes/2026-10-01-rf-003-rf-004.md)).
- 2026-10-02: CT-051 passando com o `ChunkRepository.saveAll` do RF-006 (colunas `client_id`, `document_id`, `chunk_index`, `document_type`, `file_name` e vetor de 768 dimensões conferidos no banco); todos os cenários do RF-002 passam ([evidência](../QA/evidencias/execucoes/2026-10-02-rf-005-rf-006.md)).
