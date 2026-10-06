# ADR-002 — PostgreSQL com pgvector como banco relacional e vetorial

- **Status:** Aprovada (definida no README)
- **Data:** 2026-10-01
- **Requisitos:** RF-001, RF-002, RF-007

## Contexto

O README define PostgreSQL + pgvector, Docker Compose para subir o banco e índice HNSW com distância de cosseno. O isolamento por cliente precisa juntar dado relacional (`document.client_id`) e vetor na mesma consulta.

## Decisão

- Um único PostgreSQL guarda clientes, documentos e chunks com embeddings.
- Imagem `pgvector/pgvector:pg17` no `docker-compose.yml`, com volume nomeado.
- Extensão criada em `docker/init.sql` e também na migration V1.
- Testes de integração usam a mesma imagem via Testcontainers (ADR-012).

## Alternativas consideradas

- **Banco vetorial dedicado (Qdrant, Milvus, Weaviate):** exigiria sincronizar dois bancos e fazer o filtro por cliente com metadados, fora do SQL — contraria o README.
- **Imagem `postgres` oficial + instalação manual do pgvector:** mais trabalho sem benefício.

## Consequências

- Filtro por cliente, JOIN e similaridade numa única consulta transacional.
- Versão do pgvector vem da imagem; a 0.8+ é necessária para `hnsw.iterative_scan` (04, seção 5).
- Atualizar a tag da imagem é uma decisão explícita (não usar `latest`).
