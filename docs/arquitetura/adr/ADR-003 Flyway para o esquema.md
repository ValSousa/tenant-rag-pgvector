# ADR-003 — Flyway para versionar o esquema

- **Status:** Aprovada (definida no README)
- **Data:** 2026-10-01
- **Requisitos:** RF-002

## Contexto

O README prevê `src/main/resources/db/migration/V1__create_tables.sql`, que é a convenção do Flyway. O esquema tem tipo `VECTOR`, índice HNSW e restrições que o Hibernate não gera bem.

## Decisão

- Flyway cria e evolui o esquema, **inclusive a tabela `document_chunk` do `PgVectorEmbeddingStore`** (`createTable(false)` no store, ADR-004); cada mudança é uma nova migration `V<n>__descricao.sql`, nunca edição de migration já aplicada.
- `spring.jpa.hibernate.ddl-auto=validate`: o Hibernate só confere.
- No Spring Boot 4, usar `spring-boot-starter-flyway` (a autoconfiguração saiu do núcleo) e `flyway-database-postgresql`.

## Alternativas consideradas

- **`ddl-auto=update`:** não cria índice HNSW nem `CHECK`, e muda o banco sem histórico.
- **Liquibase:** equivalente, mas o README já aponta para Flyway.

## Consequências

- Esquema reproduzível em qualquer banco, inclusive no Testcontainers.
- Mudar a dimensão do vetor exige migration nova e reprocessamento (04, seção 3).
