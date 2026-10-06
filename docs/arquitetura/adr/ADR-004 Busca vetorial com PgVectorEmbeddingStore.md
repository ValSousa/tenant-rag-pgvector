# ADR-004 — Busca vetorial com PgVectorEmbeddingStore filtrado por cliente

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-002, RF-006, RF-007, RF-012

## Histórico

| Data | Decisão |
|---|---|
| 2026-10-01 | Versão inicial: busca com SQL próprio (`JdbcClient`, `JOIN document ... WHERE d.client_id`), sem biblioteca de vector store. |
| 2026-10-01 | **Substituída por esta versão**: usar o `PgVectorEmbeddingStore` do LangChain4j, para o projeto ficar semelhante a um projeto anterior com LangChain + pgvector, que é o foco de estudo. |

## Contexto

O responsável quer estudar o mesmo padrão de um projeto anterior feito com LangChain, pgvector e um provedor de modelos (Gemini/OpenAI). Em LangChain4j, o equivalente ao `PGVector` do LangChain é o `PgVectorEmbeddingStore`. A regra de segurança do README (seção 12) continua obrigatória: a busca sempre filtra pelo cliente, e nunca depende só da similaridade.

O `PgVectorEmbeddingStore` (módulo `langchain4j-pgvector`, ainda publicado como beta) grava cada chunk como `embedding_id UUID`, `embedding vector(n)`, `text` e metadados. Os metadados podem ficar num JSON (`COMBINED_JSON`, padrão) ou **uma coluna por chave** (`COLUMN_PER_KEY`), com a chave do metadado igual ao nome da coluna. Filtros (`metadataKey(...).isEqualTo(...)`) viram `WHERE` no SQL que a própria biblioteca gera.

## Decisão

- Chunks e embeddings gravados e buscados pelo **`PgVectorEmbeddingStore`**, na tabela `document_chunk`.
- Metadados em **`COLUMN_PER_KEY`**: `client_id`, `document_id`, `chunk_index`, `document_type`, `file_name` são colunas tipadas, não JSON.
- **Tabela criada pelo Flyway**, não pela biblioteca (`createTable(false)`, `skipCreateVectorExtension(true)`), para ter:
  - índice **HNSW** com `vector_cosine_ops` (a biblioteca só cria IVFFlat);
  - chave estrangeira composta `(document_id, client_id) → document(id, client_id)`, que impede um chunk de ficar com cliente diferente do documento;
  - índice em `client_id`.
- **Toda busca passa pelo filtro** `metadataKey("client_id").isEqualTo(clientId)`, aplicado dentro de `ChunkRepository.searchByClient(clientId, ...)`. Não existe método de busca sem cliente.
- O `EmbeddingStore` só é usado dentro de `ChunkRepository` (camada repository); nenhum service o acessa diretamente.
- O `DataSource` passado ao store é envolvido em `TransactionAwareDataSourceProxy`, para que a gravação dos chunks participe da mesma transação do `DocumentEntity` (JPA).

## Alternativas consideradas

- **SQL próprio com `JdbcClient` (versão anterior):** filtro e JOIN explícitos, mais didático sobre SQL vetorial, mas não reproduz o padrão LangChain que o responsável quer estudar.
- **`PgVectorEmbeddingStore` com metadados em JSON (padrão):** zero configuração, mas o cliente ficaria num campo JSON sem chave estrangeira nem tipo.
- **`PgVectorStore` do Spring AI:** equivalente, porém o projeto passou a usar LangChain4j (ADR-005).

## Consequências

- O SQL de busca é gerado pela biblioteca: `ORDER BY embedding <=> :q LIMIT k`, com `WHERE client_id = ...` vindo do filtro e `score = (2 - distância) / 2` (0 a 1, maior é mais parecido). O teste de isolamento (RF-012) é o que garante que o filtro está presente.
- O README e o `CLAUDE.md` passam a descrever o filtro na coluna `document_chunk.client_id`, não mais por `JOIN document`.
- A coluna `client_id` é redundante com `document.client_id` (desnormalização); a chave estrangeira composta garante que as duas nunca divergem.
- `column definitions` do store e a migration precisam ter os mesmos nomes de coluna; mudar um exige mudar o outro.
- Dependência de um módulo beta: fixar a versão pelo `langchain4j-bom` e rodar os testes de isolamento a cada atualização.
- O ajuste `hnsw.iterative_scan` continua possível, executando `SET LOCAL` na mesma transação antes da busca (04, seção 5).
